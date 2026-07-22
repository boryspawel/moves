package com.motionecosystem.trainingexecution;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.audit.api.TransactionalOutbox;
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
    private final TransactionalOutbox outbox;
    private final ExecutionProjectionService projections;
    private final SessionExecutionAttemptService attempts;
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
        validatePainDifficulty(command.painLevel(), command.difficultyLevel(), command.techniqueConfidenceLevel());
        if (command.sessionRpe() != null && (command.sessionRpe() < 1 || command.sessionRpe() > 10)) {
            throw badRequest("session RPE is outside range");
        }

        Instant now = clock.instant();
        UUID executionId = UUID.randomUUID();
        UUID eventId = outbox.append("SessionExecution", executionId, "SessionExecutionDeclared",
                "{\"executionId\":\"" + executionId + "\",\"participantId\":\""
                        + participant.id() + "\",\"plannedSessionId\":\"" + plannedSessionId + "\"}", now);
        SessionExecution execution = new SessionExecution(executionId, plannedSessionId, participant.id(),
                true, key, now);
        PainDifficultyReport report = new PainDifficultyReport(UUID.randomUUID(), execution.id(),
                command.painLevel(), command.difficultyLevel(), command.techniqueConfidenceLevel(), optionalText(command.note(), 500), now);
        List<AlertData> alerts = new java.util.ArrayList<>();
        if (report.painLevel() > 0) {
            alerts.add(new AlertData(UUID.randomUUID(), execution.id(), "PAIN_REPORTED",
                        report.painLevel() >= 7 ? "HIGH" : "MEDIUM", null, "OPEN",
                        now.plusSeconds(report.painLevel() >= 7 ? 4 * 60 * 60 : 24 * 60 * 60),
                        report.id(), now));
        }
        if (report.difficultyLevel() >= 8 || (report.techniqueConfidenceLevel() != null && report.techniqueConfidenceLevel() <= 3)) {
            alerts.add(new AlertData(UUID.randomUUID(), execution.id(), "DIFFICULTY_REPORTED",
                    "MEDIUM", null, "OPEN", now.plusSeconds(24 * 60 * 60), report.id(), now));
        }
        persistence.save(new ExecutionData(execution.id(), execution.plannedSessionId(),
                        execution.participantAccountId(), execution.declaredCompletion(),
                        execution.idempotencyKey(), execution.recordedAt(), eventId, "PENDING"),
                command.results().stream().map(item -> {
                    validateResultValues(item);
                    PrescriptionReference reference = prescribed.stream()
                            .filter(value -> value.id().equals(item.exercisePrescriptionId()))
                            .findFirst().orElseThrow();
                    return new ResultData(UUID.randomUUID(), execution.id(), item.exercisePrescriptionId(),
                            reference.exerciseVersionId(), item.actualSets(), item.actualRepetitions(),
                            item.actualDurationSeconds(), item.actualContacts(), item.actualDistanceMeters(),
                            item.actualLoadKg(), item.actualExternalLoadValue(), item.actualExternalLoadUnit(),
                            item.actualIntensityType(), item.actualIntensityValue(), item.actualIntensityZone(),
                            item.side(), Boolean.TRUE.equals(item.modified()),
                            Boolean.TRUE.equals(item.skipped()), mode(item.observationMode()));
                }).toList(),
                new ReportData(report.id(), report.sessionExecutionId(), report.painLevel(),
                        report.difficultyLevel(), report.techniqueConfidenceLevel(), report.note(), command.sessionRpe(),
                        mode(command.observationMode()), report.reportedAt()), alerts);
        attempts.completeAfterFinalDeclaration(subject, participant.id(), plannedSessionId);
        plannedSessions.markCompleted(plannedSessionId);
        audit.record(subject, "SESSION_EXECUTION_DECLARED", "SessionExecution", execution.id());
        return execution(execution.id());
    }

    @Transactional
    public ExecutionView correct(String subject, UUID executionId, String idempotencyKey,
                                 CorrectionCommand command) {
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
        String key = requiredText(idempotencyKey, 120, "Idempotency-Key");
        if (persistence.findCorrectionByIdempotencyKey(executionId, key).isPresent()) {
            return execution(executionId);
        }
        String reason = requiredText(command.reason(), 500, "correction reason");
        validateOptionalPainDifficulty(command.correctedPainLevel(), command.correctedDifficultyLevel());
        validateCorrectionValues(command);
        if (command.correctedPainLevel() == null && command.correctedDifficultyLevel() == null
                && command.exercisePrescriptionId() == null) {
            throw badRequest("at least one corrected value is required");
        }
        UUID resultId = command.exercisePrescriptionId() == null ? null : persistence.findById(executionId)
                .orElseThrow().results().stream()
                .filter(item -> item.exercisePrescriptionId().equals(command.exercisePrescriptionId()))
                .map(ResultData::id).findFirst()
                .orElseThrow(() -> badRequest("corrected result must belong to execution"));
        ExecutionCorrection correction = new ExecutionCorrection(UUID.randomUUID(), executionId, actor.id(), reason,
                command.correctedPainLevel(), command.correctedDifficultyLevel(), clock.instant());
        persistence.appendCorrection(new CorrectionData(correction.id(), correction.sessionExecutionId(),
                correction.correctedByAccountId(), correction.reason(), correction.correctedPainLevel(),
                correction.correctedDifficultyLevel(), resultId, command.correctedSets(),
                command.correctedRepetitions(), command.correctedDurationSeconds(),
                command.correctedContacts(), command.correctedExternalLoadValue(),
                command.correctedExternalLoadUnit(), command.correctedSide(), command.correctedModified(),
                command.correctedSkipped(), mode(command.observationMode()),
                key, correction.correctedAt()));
        if (resultId != null) {
            boolean reversed = persistence.reverseQualification(executionId, correction.correctedAt());
            projections.rebuild(executionId);
            if (reversed) {
                outbox.append("SessionExecution", executionId, "ExecutionQualificationReversed",
                        "{\"executionId\":\"" + executionId
                                + "\",\"qualificationType\":\"COMPLETED_SESSION\"}", correction.correctedAt());
            }
        }
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
        if (results == null || results.isEmpty() || results.size() > prescribed.size()) {
            throw badRequest("at least one result from the planned session is required");
        }
        Set<UUID> expected = prescribed.stream().map(PrescriptionReference::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> actual = new HashSet<>();
        results.forEach(item -> {
            if (item == null || item.exercisePrescriptionId() == null || !actual.add(item.exercisePrescriptionId())) {
                throw badRequest("exercise result prescriptions must be present and unique");
            }
        });
        if (!expected.containsAll(actual)) {
            throw badRequest("exercise result must reference a prescription from the planned session");
        }
    }

    private static void validateResultValues(ResultCommand result) {
        if (result.actualSets() != null && result.actualSets() < 0
                || result.actualRepetitions() != null && result.actualRepetitions() < 0
                || result.actualDurationSeconds() != null && result.actualDurationSeconds() < 0
                || result.actualContacts() != null && result.actualContacts() < 0
                || result.actualDistanceMeters() != null && result.actualDistanceMeters().signum() < 0
                || result.actualLoadKg() != null && result.actualLoadKg().signum() < 0
                || result.actualExternalLoadValue() != null && result.actualExternalLoadValue().signum() < 0
                || result.actualIntensityValue() != null && result.actualIntensityValue().signum() < 0) {
            throw badRequest("exercise result values are outside range");
        }
        if (result.side() != null && !Set.of("LEFT", "RIGHT", "BILATERAL", "NOT_APPLICABLE")
                .contains(result.side())) {
            throw badRequest("exercise result side is invalid");
        }
        if (Boolean.TRUE.equals(result.skipped()) && (positive(result.actualSets())
                || positive(result.actualRepetitions()) || positive(result.actualDurationSeconds())
                || positive(result.actualContacts()))) {
            throw badRequest("skipped result cannot contain a positive dose");
        }
        if (!Set.of("DECLARED", "DEVICE", "ESTIMATED").contains(mode(result.observationMode()))) {
            throw badRequest("observation mode is invalid");
        }
    }

    private static void validatePainDifficulty(int pain, int difficulty, Integer confidence) {
        if (pain < 0 || pain > 10 || difficulty < 1 || difficulty > 10 || confidence != null && (confidence < 1 || confidence > 10)) {
            throw badRequest("pain or difficulty level is outside range");
        }
    }

    private static void validateOptionalPainDifficulty(Integer pain, Integer difficulty) {
        if (pain != null && (pain < 0 || pain > 10)
                || difficulty != null && (difficulty < 1 || difficulty > 10)) {
            throw badRequest("corrected pain or difficulty level is outside range");
        }
    }

    private static void validateCorrectionValues(CorrectionCommand command) {
        if (negative(command.correctedSets()) || negative(command.correctedRepetitions())
                || negative(command.correctedDurationSeconds()) || negative(command.correctedContacts())
                || command.correctedExternalLoadValue() != null
                && command.correctedExternalLoadValue().signum() < 0) {
            throw badRequest("corrected exercise result values are outside range");
        }
        if (command.correctedSide() != null
                && !Set.of("LEFT", "RIGHT", "BILATERAL", "NOT_APPLICABLE")
                .contains(command.correctedSide())) {
            throw badRequest("corrected exercise result side is invalid");
        }
        if (!Set.of("DECLARED", "DEVICE", "ESTIMATED").contains(mode(command.observationMode()))) {
            throw badRequest("observation mode is invalid");
        }
    }

    private static boolean negative(Integer value) {
        return value != null && value < 0;
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
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
                execution.declaredCompletion(), execution.recordedAt(), report.painLevel(), report.difficultyLevel(), report.techniqueConfidenceLevel(),
                report.note(), report.sessionRpe(), report.observationMode(),
                aggregate.results().stream().map(item -> new ResultView(
                item.exercisePrescriptionId(), item.exerciseVersionId(), item.actualSets(),
                item.actualRepetitions(), item.actualDurationSeconds(), item.actualContacts(),
                item.actualDistanceMeters(), item.actualLoadKg(), item.actualExternalLoadValue(),
                item.actualExternalLoadUnit(), item.actualIntensityType(), item.actualIntensityValue(),
                item.actualIntensityZone(), item.side(), item.modified(), item.skipped(),
                item.observationMode())).toList(), aggregate.corrections().stream().map(item -> new CorrectionView(
                item.id(), item.reason(), item.correctedPainLevel(), item.correctedDifficultyLevel(),
                item.correctedResultId(), item.correctedSets(), item.correctedRepetitions(),
                item.correctedDurationSeconds(), item.correctedContacts(), item.correctedExternalLoadValue(),
                item.correctedExternalLoadUnit(), item.correctedSide(), item.correctedModified(),
                item.correctedSkipped(), item.observationMode(), item.correctedAt())).toList(),
                aggregate.alerts().stream().map(AlertData::alertType).toList(), aggregate.alerts());
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

    private static String mode(String value) {
        return value == null || value.isBlank() ? "DECLARED" : value;
    }

    public record DeclareExecutionCommand(boolean declaredCompletion, List<ResultCommand> results,
                                          int painLevel, int difficultyLevel, Integer techniqueConfidenceLevel, String note,
                                          Integer sessionRpe, String observationMode) {
    }

    public record ResultCommand(
            UUID exercisePrescriptionId, Integer actualSets, Integer actualRepetitions,
            Integer actualDurationSeconds, Integer actualContacts, BigDecimal actualDistanceMeters,
            BigDecimal actualLoadKg, BigDecimal actualExternalLoadValue, String actualExternalLoadUnit,
            String actualIntensityType, BigDecimal actualIntensityValue, String actualIntensityZone,
            String side, Boolean modified, Boolean skipped, String observationMode) {
    }

    public record CorrectionCommand(
            String reason, Integer correctedPainLevel, Integer correctedDifficultyLevel,
            UUID exercisePrescriptionId, Integer correctedSets, Integer correctedRepetitions,
            Integer correctedDurationSeconds, Integer correctedContacts,
            BigDecimal correctedExternalLoadValue, String correctedExternalLoadUnit,
            String correctedSide, Boolean correctedModified, Boolean correctedSkipped,
            String observationMode) {
    }

    public record ExecutionView(UUID id, UUID plannedSessionId, UUID participantAccountId,
                                 boolean declaredCompletion, Instant recordedAt, int painLevel,
                                 int difficultyLevel, Integer techniqueConfidenceLevel, String note, Integer sessionRpe,
                                String observationMode, List<ResultView> results,
                                List<CorrectionView> corrections, List<String> alerts,
                                List<AlertData> safetyAlerts) {
    }

    public record ResultView(
            UUID exercisePrescriptionId, UUID exerciseVersionId, Integer actualSets,
            Integer actualRepetitions, Integer actualDurationSeconds, Integer actualContacts,
            BigDecimal actualDistanceMeters, BigDecimal actualLoadKg,
            BigDecimal actualExternalLoadValue, String actualExternalLoadUnit,
            String actualIntensityType, BigDecimal actualIntensityValue, String actualIntensityZone, String side,
            boolean modified, boolean skipped, String observationMode) {
    }

    public record CorrectionView(UUID id, String reason, Integer correctedPainLevel,
                                 Integer correctedDifficultyLevel, UUID correctedResultId,
                                 Integer correctedSets, Integer correctedRepetitions,
                                 Integer correctedDurationSeconds, Integer correctedContacts,
                                 BigDecimal correctedExternalLoadValue, String correctedExternalLoadUnit,
                                 String correctedSide, Boolean correctedModified, Boolean correctedSkipped,
                                 String observationMode, Instant correctedAt) {
    }
}
