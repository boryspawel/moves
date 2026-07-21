package com.motionecosystem.trainingexecution;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.AlertData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.CorrectionData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ExecutionAggregate;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ExecutionData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ReportData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ResultData;
import com.motionecosystem.trainingplanning.api.PlannedSessionExecutionPort;
import com.motionecosystem.trainingplanning.api.PlannedSessionExecutionPort.SessionState;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SessionExecutionService {

    private final CurrentAccountService accounts;
    private final SpecialistRelationshipService relationships;
    private final PlannedSessionExecutionPort plannedSessions;
    private final SessionExecutionPersistence persistence;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public ExecutionView declare(String subject, UUID plannedSessionId, String idempotencyKey,
                                 DeclareExecutionCommand command) {
        CurrentAccount participant = accounts.requireActive(subject);
        requireProfile(participant, ProfileType.PARTICIPANT, "participant profile is required");
        String key = requiredText(idempotencyKey, 120, "Idempotency-Key");

        var existing = persistence.findByParticipantAndIdempotencyKey(participant.id(), key);
        if (existing.isPresent()) {
            ExecutionView result = view(existing.get());
            if (!result.plannedSessionId().equals(plannedSessionId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "idempotency key was already used for another session");
            }
            return result;
        }

        if (command == null || !command.declaredCompletion()) {
            throw badRequest("declaredCompletion must be explicitly true");
        }
        var plannedSession = plannedSessions.lockOwnedSession(plannedSessionId, participant.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "assigned session not found"));

        existing = persistence.findByParticipantAndIdempotencyKey(participant.id(), key);
        if (existing.isPresent()) {
            ExecutionView result = view(existing.get());
            if (!result.plannedSessionId().equals(plannedSessionId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "idempotency key was already used for another session");
            }
            return result;
        }
        if (persistence.findByPlannedSessionId(plannedSessionId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "planned session already has a successful execution");
        }
        if (plannedSession.state() != SessionState.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "assigned session not found");
        }
        List<PrescriptionReference> prescribed = plannedSession.prescriptions().stream()
                .map(item -> new PrescriptionReference(item.id(), item.exerciseVersionId()))
                .toList();
        validateResults(prescribed, command.results());
        validatePainDifficulty(command.painLevel(), command.difficultyLevel());

        Instant now = clock.instant();
        SessionExecution execution = new SessionExecution(UUID.randomUUID(), plannedSessionId, participant.id(),
                true, key, now);
        List<ExerciseResult> results = command.results().stream().map(requested -> {
            validateResultValues(requested);
            return new ExerciseResult(UUID.randomUUID(), execution.id(),
                    requested.exercisePrescriptionId(), requested.actualRepetitions(),
                    requested.actualDurationSeconds(), requested.actualLoadKg());
        }).toList();

        PainDifficultyReport report = new PainDifficultyReport(UUID.randomUUID(), execution.id(),
                command.painLevel(), command.difficultyLevel(), optionalText(command.note(), 500), now);
        List<AlertData> alerts = report.painLevel() > 0
                ? List.of(new AlertData(UUID.randomUUID(), execution.id(), "PAIN_REPORTED", now))
                : List.of();
        persistence.save(new ExecutionData(execution.id(), execution.plannedSessionId(),
                        execution.participantAccountId(), execution.declaredCompletion(),
                        execution.idempotencyKey(), execution.recordedAt()),
                results.stream().map(item -> new ResultData(item.id(), item.sessionExecutionId(),
                        item.exercisePrescriptionId(), item.actualRepetitions(), item.actualDurationSeconds(),
                        item.actualLoadKg())).toList(),
                new ReportData(report.id(), report.sessionExecutionId(), report.painLevel(),
                        report.difficultyLevel(), report.note(), report.reportedAt()), alerts);
        plannedSessions.markCompleted(plannedSessionId);
        audit.record(subject, "SESSION_EXECUTION_DECLARED", "SessionExecution", execution.id());
        return execution(execution.id());
    }

    @Transactional
    public ExecutionView correct(String subject, UUID executionId, CorrectionCommand command) {
        CurrentAccount actor = accounts.requireActive(subject);
        ExecutionOwner owner = owner(executionId);
        if (actor.profileType() == ProfileType.PARTICIPANT) {
            if (!actor.id().equals(owner.participantAccountId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "execution belongs to another participant");
            }
        } else if (actor.profileType() == ProfileType.SPECIALIST) {
            relationships.requireActive(actor.id(), owner.participantAccountId());
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "profile is not allowed to correct execution");
        }
        if (command == null) {
            throw badRequest("correction is required");
        }
        String reason = requiredText(command.reason(), 500, "correction reason");
        validateOptionalPainDifficulty(command.correctedPainLevel(), command.correctedDifficultyLevel());
        if (command.correctedPainLevel() == null && command.correctedDifficultyLevel() == null) {
            throw badRequest("at least one corrected value is required");
        }
        ExecutionCorrection correction = new ExecutionCorrection(UUID.randomUUID(), executionId, actor.id(), reason,
                command.correctedPainLevel(), command.correctedDifficultyLevel(), clock.instant());
        persistence.appendCorrection(new CorrectionData(correction.id(), correction.sessionExecutionId(),
                correction.correctedByAccountId(), correction.reason(), correction.correctedPainLevel(),
                correction.correctedDifficultyLevel(), correction.correctedAt()));
        audit.record(subject, "SESSION_EXECUTION_CORRECTED", "SessionExecution", executionId);
        return execution(executionId);
    }

    @Transactional(readOnly = true)
    public List<ExecutionView> specialistExecutions(String subject, UUID participantAccountId) {
        CurrentAccount specialist = accounts.requireActive(subject);
        requireProfile(specialist, ProfileType.SPECIALIST, "specialist profile is required");
        relationships.requireActive(specialist.id(), participantAccountId);
        return persistence.findByParticipant(participantAccountId).stream().map(this::view).toList();
    }

    private static void validateResults(List<PrescriptionReference> prescribed, List<ResultCommand> results) {
        if (results == null || results.size() != prescribed.size()) {
            throw badRequest("one result for every prescribed exercise is required");
        }
        Set<UUID> expected = prescribed.stream().map(PrescriptionReference::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> actual = new HashSet<>();
        results.forEach(item -> {
            if (item == null || item.exercisePrescriptionId() == null || !actual.add(item.exercisePrescriptionId())) {
                throw badRequest("exercise result prescriptions must be present and unique");
            }
        });
        if (!actual.equals(expected)) {
            throw badRequest("exercise result must reference a prescription from the planned session");
        }
    }

    private static void validateResultValues(ResultCommand result) {
        if (result.actualRepetitions() != null && result.actualRepetitions() < 0
                || result.actualDurationSeconds() != null && result.actualDurationSeconds() < 0
                || result.actualLoadKg() != null && result.actualLoadKg().signum() < 0) {
            throw badRequest("exercise result values are outside range");
        }
    }

    private static void validatePainDifficulty(int pain, int difficulty) {
        if (pain < 0 || pain > 10 || difficulty < 1 || difficulty > 10) {
            throw badRequest("pain or difficulty level is outside range");
        }
    }

    private static void validateOptionalPainDifficulty(Integer pain, Integer difficulty) {
        if (pain != null && (pain < 0 || pain > 10)
                || difficulty != null && (difficulty < 1 || difficulty > 10)) {
            throw badRequest("corrected pain or difficulty level is outside range");
        }
    }

    private ExecutionOwner owner(UUID executionId) {
        return persistence.findOwner(executionId)
                .map(item -> new ExecutionOwner(item.executionId(), item.participantAccountId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "session execution not found"));
    }

    private ExecutionView execution(UUID executionId) {
        return persistence.findById(executionId).map(this::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "session execution not found"));
    }

    private ExecutionView view(ExecutionAggregate aggregate) {
        ExecutionData execution = aggregate.execution();
        ReportData report = aggregate.report();
        return new ExecutionView(execution.id(), execution.plannedSessionId(), execution.participantAccountId(),
                execution.declaredCompletion(), execution.recordedAt(), report.painLevel(), report.difficultyLevel(),
                report.note(), aggregate.results().stream().map(item -> new ResultView(
                item.exercisePrescriptionId(), item.actualRepetitions(), item.actualDurationSeconds(),
                item.actualLoadKg())).toList(), aggregate.corrections().stream().map(item -> new CorrectionView(
                item.id(), item.reason(), item.correctedPainLevel(), item.correctedDifficultyLevel(),
                item.correctedAt())).toList(), aggregate.alerts().stream().map(AlertData::alertType).toList());
    }

    private static void requireProfile(CurrentAccount account, ProfileType expected, String message) {
        if (account.profileType() != expected) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private static String requiredText(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw badRequest(field + " is required and too long values are rejected");
        }
        return normalized;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw badRequest("note is too long");
        }
        return normalized;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record PrescriptionReference(UUID id, UUID exerciseVersionId) {
    }

    private record ExecutionOwner(UUID id, UUID participantAccountId) {
    }

    public record DeclareExecutionCommand(boolean declaredCompletion, List<ResultCommand> results,
                                          int painLevel, int difficultyLevel, String note) {
    }

    public record ResultCommand(UUID exercisePrescriptionId, Integer actualRepetitions,
                                Integer actualDurationSeconds, BigDecimal actualLoadKg) {
    }

    public record CorrectionCommand(String reason, Integer correctedPainLevel,
                                    Integer correctedDifficultyLevel) {
    }

    public record ExecutionView(UUID id, UUID plannedSessionId, UUID participantAccountId,
                                boolean declaredCompletion, Instant recordedAt, int painLevel,
                                int difficultyLevel, String note, List<ResultView> results,
                                List<CorrectionView> corrections, List<String> alerts) {
    }

    public record ResultView(UUID exercisePrescriptionId, Integer actualRepetitions,
                             Integer actualDurationSeconds, BigDecimal actualLoadKg) {
    }

    public record CorrectionView(UUID id, String reason, Integer correctedPainLevel,
                                 Integer correctedDifficultyLevel, Instant correctedAt) {
    }
}
