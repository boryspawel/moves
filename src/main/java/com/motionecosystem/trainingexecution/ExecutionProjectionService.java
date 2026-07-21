package com.motionecosystem.trainingexecution;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.audit.api.TransactionalOutbox;
import com.motionecosystem.audit.api.TransactionalOutbox.OutboxMessage;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.CalculationRoleValue;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.ContributionSnapshot;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.SideRuleValue;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.AlertData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.CorrectionData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ExecutedObservationData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.Post24hData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ResultData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExecutionProjectionService {

    static final String CALCULATOR_VERSION = "executed-load-v1";

    private final SessionExecutionPersistence persistence;
    private final ExerciseCatalogQueryPort catalog;
    private final CurrentAccountService accounts;
    private final SpecialistRelationshipService relationships;
    private final TransactionalOutbox outbox;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public ProjectionResult project(UUID executionId) {
        return calculateAndSave(executionId, false);
    }

    /** Idempotent handler for the declaration outbox event. */
    @Transactional
    public ProjectionResult consume(OutboxMessage message) {
        if (message == null || !"SessionExecutionDeclared".equals(message.eventType())) {
            throw new IllegalArgumentException("SessionExecutionDeclared event is required");
        }
        var execution = persistence.findById(message.aggregateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "execution not found"));
        if (!message.id().equals(execution.execution().declarationEventId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "declaration event does not match execution");
        }
        return calculateAndSave(message.aggregateId(), false);
    }

    @Transactional
    public ProjectionResult rebuild(UUID executionId) {
        return calculateAndSave(executionId, true);
    }

    /** Retries declarations left pending or failed before the supplied operational cut-off. */
    @Transactional
    public RecoveryResult recover(Instant recordedBefore) {
        int projected = 0;
        int failed = 0;
        for (UUID executionId : persistence.findProjectionCandidates(recordedBefore)) {
            try {
                if (calculateAndSave(executionId, false).created()) projected++;
            } catch (RuntimeException exception) {
                persistence.markProjectionFailed(executionId);
                failed++;
            }
        }
        return new RecoveryResult(projected, failed);
    }

    private ProjectionResult calculateAndSave(UUID executionId, boolean rebuild) {
        var aggregate = persistence.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "execution not found"));
        Set<UUID> versionIds = aggregate.results().stream().map(ResultData::exerciseVersionId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot> profiles =
                catalog.findPublishedVersions(versionIds);
        if (profiles.size() != versionIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "execution references an unavailable exercise profile");
        }
        List<ExecutedObservationData> observations = new ArrayList<>();
        for (ResultData stored : aggregate.results()) {
            ResultData result = corrected(stored, aggregate.corrections());
            var profile = profiles.get(result.exerciseVersionId());
            if (profile == null) continue;
            for (ContributionSnapshot contribution : profile.contributions()) {
                if (contribution.calculationRole() != CalculationRoleValue.ALLOCATION
                        || contribution.variantCondition() != null
                        && !"STANDARD".equals(contribution.variantCondition())) continue;
                Dose dose = dose(result, contribution.loadChannel().name());
                if (dose == null) continue;
                observations.add(new ExecutedObservationData(
                        UUID.randomUUID(), executionId, result.id(), result.exerciseVersionId(),
                        contribution.anatomicalStructureId(), side(result.side(), contribution.sideRule()),
                        contribution.loadChannel().name(), dose.unit(),
                        dose.value().multiply(contribution.coefficientLow()).setScale(6, RoundingMode.HALF_UP),
                        dose.value().multiply(contribution.coefficientHigh()).setScale(6, RoundingMode.HALF_UP),
                        result.observationMode(), CALCULATOR_VERSION, aggregate.execution().recordedAt()));
            }
        }
        Instant now = clock.instant();
        boolean created = rebuild
                ? persistence.rebuildProjection(executionId, observations, now)
                : persistence.saveProjection(executionId, observations, now);
        if (created) {
            outbox.append("SessionExecution", executionId, "ExecutedLoadProjected",
                    "{\"executionId\":\"" + executionId + "\",\"calculatorVersion\":\""
                            + CALCULATOR_VERSION + "\"}", now);
            if (!rebuild) {
                outbox.append("SessionExecution", executionId, "ExecutionQualifiedForGamification",
                        "{\"executionId\":\"" + executionId
                                + "\",\"qualificationType\":\"COMPLETED_SESSION\"}", now);
            }
        }
        return new ProjectionResult(executionId, observations.size(), created, CALCULATOR_VERSION);
    }

    @Transactional
    public Post24hData reportPost24h(
            String subject, UUID executionId, String idempotencyKey, Post24hCommand command) {
        CurrentAccount participant = accounts.requireActive(subject);
        if (!participant.hasProfile(ProfileType.PARTICIPANT)) throw forbidden("participant profile is required");
        var owner = persistence.findOwner(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "execution not found"));
        if (!participant.id().equals(owner.participantAccountId())) throw forbidden("execution belongs to another participant");
        String key = required(idempotencyKey, 120, "Idempotency-Key");
        var existing = persistence.findPost24h(executionId, key);
        if (existing.isPresent()) return existing.get();
        if (command == null || command.painLevel() < 0 || command.painLevel() > 10
                || command.difficultyLevel() < 1 || command.difficultyLevel() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "post-24h response values are invalid");
        }
        Instant now = clock.instant();
        Post24hData saved = persistence.savePost24h(new Post24hData(
                UUID.randomUUID(), executionId, participant.id(), command.painLevel(),
                command.difficultyLevel(), optional(command.note(), 500), mode(command.observationMode()),
                key, now));
        outbox.append("SessionExecution", executionId, "Post24hResponseRecorded",
                "{\"executionId\":\"" + executionId + "\",\"responseId\":\""
                        + saved.id() + "\"}", now);
        audit.record(subject, "POST_24H_RESPONSE_RECORDED", "SessionExecution", executionId);
        return saved;
    }

    @Transactional
    public AlertData transitionAlert(
            String subject, UUID executionId, UUID alertId, AlertTransitionCommand command) {
        CurrentAccount actor = accounts.requireActive(subject);
        var owner = persistence.findOwner(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "execution not found"));
        boolean participantContext = actor.hasProfile(ProfileType.PARTICIPANT);
        if (participantContext) {
            if (!actor.id().equals(owner.participantAccountId())) throw forbidden("execution belongs to another participant");
        } else if (actor.hasProfile(ProfileType.SPECIALIST)) {
            relationships.requireActive(actor.id(), owner.participantAccountId());
        } else throw forbidden("profile cannot manage execution alerts");
        if (command == null || !Set.of("ACKNOWLEDGE", "RESOLVE", "REOPEN", "ASSIGN").contains(command.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "supported alert action is required");
        }
        if (participantContext && !"ACKNOWLEDGE".equals(command.action())) {
            throw forbidden("participant can only acknowledge an alert");
        }
        if ("ASSIGN".equals(command.action()) && !actor.id().equals(command.ownerAccountId())) {
            throw forbidden("specialist can only assign the alert to the active specialist context");
        }
        AlertData result;
        try {
            result = persistence.transitionAlert(alertId, actor.id(), command.action(),
                            command.ownerAccountId(), optional(command.commentReference(), 500), clock.instant())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "alert not found"));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        if (!result.sessionExecutionId().equals(executionId)) throw forbidden("alert belongs to another execution");
        audit.record(subject, "EXECUTION_ALERT_" + command.action(), "ExecutionAlert", alertId);
        return result;
    }

    private static Dose dose(ResultData result, String channel) {
        if (result.skipped()) return new Dose(BigDecimal.ZERO, unit(channel));
        int sets = result.actualSets() == null ? 1 : result.actualSets();
        return switch (channel) {
            case "DYN_EXU" -> result.actualRepetitions() == null ? null
                    : new Dose(BigDecimal.valueOf((long) sets * result.actualRepetitions()), "EXU");
            case "ISO_SEC" -> result.actualDurationSeconds() == null ? null
                    : new Dose(BigDecimal.valueOf((long) sets * result.actualDurationSeconds()), "s");
            case "IMPACT_CONTACTS" -> result.actualContacts() == null ? null
                    : new Dose(BigDecimal.valueOf((long) sets * result.actualContacts()), "contacts");
            case "ENDURANCE_MIN_ZONE" -> result.actualDurationSeconds() == null ? null
                    : new Dose(BigDecimal.valueOf(result.actualDurationSeconds())
                            .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP), "min");
            default -> null;
        };
    }

    private static ResultData corrected(ResultData original, List<CorrectionData> corrections) {
        ResultData value = original;
        for (CorrectionData correction : corrections) {
            if (!original.id().equals(correction.correctedResultId())) continue;
            value = new ResultData(value.id(), value.sessionExecutionId(), value.exercisePrescriptionId(),
                    value.exerciseVersionId(), first(correction.correctedSets(), value.actualSets()),
                    first(correction.correctedRepetitions(), value.actualRepetitions()),
                    first(correction.correctedDurationSeconds(), value.actualDurationSeconds()),
                    first(correction.correctedContacts(), value.actualContacts()), value.actualDistanceMeters(),
                    value.actualLoadKg(), first(correction.correctedExternalLoadValue(), value.actualExternalLoadValue()),
                    first(correction.correctedExternalLoadUnit(), value.actualExternalLoadUnit()),
                    value.actualIntensityType(), value.actualIntensityValue(), value.actualIntensityZone(),
                    first(correction.correctedSide(), value.side()),
                    first(correction.correctedModified(), value.modified()),
                    first(correction.correctedSkipped(), value.skipped()), correction.observationMode());
        }
        return value;
    }

    private static <T> T first(T corrected, T original) {
        return corrected == null ? original : corrected;
    }

    private static String unit(String channel) {
        return switch (channel) {
            case "DYN_EXU" -> "EXU"; case "ISO_SEC" -> "s";
            case "IMPACT_CONTACTS" -> "contacts"; default -> "min";
        };
    }

    private static String side(String prescribed, SideRuleValue rule) {
        return switch (rule) {
            case AS_PRESCRIBED -> prescribed == null ? "NOT_APPLICABLE" : prescribed;
            case BILATERAL -> "BILATERAL"; case LEFT -> "LEFT"; case RIGHT -> "RIGHT";
            case NOT_APPLICABLE -> "NOT_APPLICABLE";
        };
    }

    private static String mode(String value) {
        String mode = value == null || value.isBlank() ? "DECLARED" : value;
        if (!Set.of("DECLARED", "DEVICE", "ESTIMATED").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "observation mode is invalid");
        }
        return mode;
    }

    private static String required(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value is too long");
        return normalized;
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private record Dose(BigDecimal value, String unit) { }
    public record ProjectionResult(UUID executionId, int observationCount, boolean created, String calculatorVersion) { }
    public record RecoveryResult(int projected, int failed) { }
    public record Post24hCommand(int painLevel, int difficultyLevel, String note, String observationMode) { }
    public record AlertTransitionCommand(String action, UUID ownerAccountId, String commentReference) { }
}
