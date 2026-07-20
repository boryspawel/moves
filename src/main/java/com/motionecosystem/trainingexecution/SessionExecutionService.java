package com.motionecosystem.trainingexecution;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exercisecatalog.CatalogService;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.safety.ParticipantSafetyService;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SessionExecutionService {

    private final JdbcTemplate jdbc;
    private final CurrentAccountService accounts;
    private final SpecialistRelationshipService relationships;
    private final CatalogService catalog;
    private final ParticipantSafetyService safety;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public ExecutionView declare(String subject, UUID plannedSessionId, String idempotencyKey,
                                 DeclareExecutionCommand command) {
        CurrentAccount participant = accounts.requireActive(subject);
        requireProfile(participant, ProfileType.PARTICIPANT, "participant profile is required");
        String key = requiredText(idempotencyKey, 120, "Idempotency-Key");

        List<UUID> existing = jdbc.query("""
                SELECT id FROM training_execution.session_execution
                WHERE participant_account_id = ? AND idempotency_key = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), participant.id(), key);
        if (!existing.isEmpty()) {
            ExecutionView result = execution(existing.getFirst());
            if (!result.plannedSessionId().equals(plannedSessionId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "idempotency key was already used for another session");
            }
            return result;
        }

        if (command == null || !command.declaredCompletion()) {
            throw badRequest("declaredCompletion must be explicitly true");
        }
        requireAssignedSession(plannedSessionId, participant.id());
        List<PrescriptionReference> prescribed = prescriptionReferences(plannedSessionId);
        validateResults(prescribed, command.results());
        requireNotHardBlocked(subject, prescribed);
        validatePainDifficulty(command.painLevel(), command.difficultyLevel());

        Instant now = clock.instant();
        SessionExecution execution = new SessionExecution(UUID.randomUUID(), plannedSessionId, participant.id(),
                true, key, now);
        jdbc.update("""
                INSERT INTO training_execution.session_execution
                    (id, planned_session_id, participant_account_id, declared_completion, idempotency_key, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, execution.id(), execution.plannedSessionId(), execution.participantAccountId(),
                execution.declaredCompletion(), execution.idempotencyKey(), Timestamp.from(execution.recordedAt()));

        command.results().forEach(requested -> {
            validateResultValues(requested);
            ExerciseResult result = new ExerciseResult(UUID.randomUUID(), execution.id(),
                    requested.exercisePrescriptionId(), requested.actualRepetitions(),
                    requested.actualDurationSeconds(), requested.actualLoadKg());
            jdbc.update("""
                    INSERT INTO training_execution.exercise_result
                        (id, session_execution_id, exercise_prescription_id, actual_repetitions,
                         actual_duration_seconds, actual_load_kg)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, result.id(), result.sessionExecutionId(), result.exercisePrescriptionId(),
                    result.actualRepetitions(), result.actualDurationSeconds(), result.actualLoadKg());
        });

        PainDifficultyReport report = new PainDifficultyReport(UUID.randomUUID(), execution.id(),
                command.painLevel(), command.difficultyLevel(), optionalText(command.note(), 500), now);
        jdbc.update("""
                INSERT INTO training_execution.pain_difficulty_report
                    (id, session_execution_id, pain_level, difficulty_level, note, reported_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, report.id(), report.sessionExecutionId(), report.painLevel(), report.difficultyLevel(),
                report.note(), Timestamp.from(report.reportedAt()));
        if (report.painLevel() > 0) {
            jdbc.update("""
                    INSERT INTO training_execution.execution_alert
                        (id, session_execution_id, alert_type, created_at)
                    VALUES (?, ?, 'PAIN_REPORTED', ?)
                    """, UUID.randomUUID(), execution.id(), Timestamp.from(now));
        }
        jdbc.update("UPDATE training_planning.planned_session SET status = 'COMPLETED' WHERE id = ?", plannedSessionId);
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
        jdbc.update("""
                INSERT INTO training_execution.execution_correction
                    (id, session_execution_id, corrected_by_account_id, reason,
                     corrected_pain_level, corrected_difficulty_level, corrected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, correction.id(), correction.sessionExecutionId(), correction.correctedByAccountId(),
                correction.reason(), correction.correctedPainLevel(), correction.correctedDifficultyLevel(),
                Timestamp.from(correction.correctedAt()));
        audit.record(subject, "SESSION_EXECUTION_CORRECTED", "SessionExecution", executionId);
        return execution(executionId);
    }

    @Transactional(readOnly = true)
    public List<ExecutionView> specialistExecutions(String subject, UUID participantAccountId) {
        CurrentAccount specialist = accounts.requireActive(subject);
        requireProfile(specialist, ProfileType.SPECIALIST, "specialist profile is required");
        relationships.requireActive(specialist.id(), participantAccountId);
        return jdbc.query("""
                SELECT id FROM training_execution.session_execution
                WHERE participant_account_id = ? ORDER BY recorded_at DESC, id
                """, (rs, row) -> execution(rs.getObject(1, UUID.class)), participantAccountId);
    }

    private void requireNotHardBlocked(String subject, List<PrescriptionReference> prescribed) {
        Set<String> restrictions = Set.copyOf(safety.current(subject).contraindicationTags());
        boolean blocked = prescribed.stream()
                .map(PrescriptionReference::exerciseVersionId)
                .map(catalog::published)
                .flatMap(version -> version.contraindicationTags().stream())
                .anyMatch(restrictions::contains);
        if (blocked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "session execution is blocked by an explicit participant restriction");
        }
    }

    private void requireAssignedSession(UUID sessionId, UUID participantAccountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM training_planning.planned_session
                WHERE id = ? AND participant_account_id = ? AND status = 'ASSIGNED'
                """, Integer.class, sessionId, participantAccountId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "assigned session not found");
        }
    }

    private List<PrescriptionReference> prescriptionReferences(UUID sessionId) {
        return jdbc.query("""
                SELECT id, exercise_version_id FROM training_planning.exercise_prescription
                WHERE planned_session_id = ? ORDER BY position
                """, (rs, row) -> new PrescriptionReference(
                rs.getObject("id", UUID.class), rs.getObject("exercise_version_id", UUID.class)), sessionId);
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
        List<ExecutionOwner> owners = jdbc.query("""
                SELECT id, participant_account_id FROM training_execution.session_execution WHERE id = ?
                """, (rs, row) -> new ExecutionOwner(
                rs.getObject("id", UUID.class), rs.getObject("participant_account_id", UUID.class)), executionId);
        if (owners.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session execution not found");
        }
        return owners.getFirst();
    }

    private ExecutionView execution(UUID executionId) {
        List<ExecutionView> executions = jdbc.query("""
                SELECT e.id, e.planned_session_id, e.participant_account_id, e.declared_completion,
                       e.recorded_at, r.pain_level, r.difficulty_level, r.note
                FROM training_execution.session_execution e
                JOIN training_execution.pain_difficulty_report r ON r.session_execution_id = e.id
                WHERE e.id = ?
                """, (rs, row) -> new ExecutionView(
                rs.getObject("id", UUID.class), rs.getObject("planned_session_id", UUID.class),
                rs.getObject("participant_account_id", UUID.class), rs.getBoolean("declared_completion"),
                rs.getTimestamp("recorded_at").toInstant(), rs.getInt("pain_level"),
                rs.getInt("difficulty_level"), rs.getString("note"), results(executionId),
                corrections(executionId), alerts(executionId)), executionId);
        if (executions.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session execution not found");
        }
        return executions.getFirst();
    }

    private List<ResultView> results(UUID executionId) {
        return jdbc.query("""
                SELECT exercise_prescription_id, actual_repetitions, actual_duration_seconds, actual_load_kg
                FROM training_execution.exercise_result WHERE session_execution_id = ?
                ORDER BY exercise_prescription_id
                """, (rs, row) -> new ResultView(rs.getObject("exercise_prescription_id", UUID.class),
                (Integer) rs.getObject("actual_repetitions"),
                (Integer) rs.getObject("actual_duration_seconds"), rs.getBigDecimal("actual_load_kg")), executionId);
    }

    private List<CorrectionView> corrections(UUID executionId) {
        return jdbc.query("""
                SELECT id, reason, corrected_pain_level, corrected_difficulty_level, corrected_at
                FROM training_execution.execution_correction
                WHERE session_execution_id = ? ORDER BY corrected_at, id
                """, (rs, row) -> new CorrectionView(rs.getObject("id", UUID.class), rs.getString("reason"),
                (Integer) rs.getObject("corrected_pain_level"),
                (Integer) rs.getObject("corrected_difficulty_level"),
                rs.getTimestamp("corrected_at").toInstant()), executionId);
    }

    private List<String> alerts(UUID executionId) {
        return jdbc.query("""
                SELECT alert_type FROM training_execution.execution_alert
                WHERE session_execution_id = ? ORDER BY alert_type
                """, (rs, row) -> rs.getString(1), executionId);
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
