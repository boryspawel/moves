package com.motionecosystem.trainingexecution;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort;
import com.motionecosystem.trainingexecution.api.SessionStartAuthorizationPort;
import com.motionecosystem.trainingexecution.api.ExecutionAdherencePort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import com.motionecosystem.trainingplanning.api.PlannedSessionExecutionPort;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SessionExecutionAttemptService implements SessionExecutionProgressQueryPort {
    private final CurrentAccountService accounts;
    private final ParticipantClientPort participants;
    private final PlannedSessionExecutionPort sessions;
    private final SessionExecutionAttemptRepository attempts;
    private final SessionExecutionAttemptProgressRepository progress;
    private final SessionExecutionPersistence executions;
    private final SessionExecutionService executionService;
    private final PlanRevisionQueryPort revisions;
    private final SessionSafetyDecisionQueryPort safety;
    private final SessionStartAuthorizationPort startAuthorization;
    private final ExecutionAdherencePort adherence;
    private final AdherenceMetricsService metrics;
    private final AuditRecorder audit;
    private final Clock clock;
    private final SessionExecutionAttemptFactRepository facts;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Transactional
    public AttemptView start(String subject, UUID plannedSessionId, UUID planRevisionId, String selectedVariantType,
                             String idempotencyKey) {
        UUID participant = participant(subject);
        String key = requiredKey(idempotencyKey);
        var replay = attempts.findByParticipantAccountIdAndStartIdempotencyKey(participant, key);
        if (replay.isPresent()) {
            if (!replay.get().plannedSessionId.equals(plannedSessionId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "idempotency key was already used for another session");
            return view(replay.get());
        }
        var planned = sessions.findSession(plannedSessionId).filter(session -> participant.equals(session.participantAccountId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "planned session not found"));
        if (planned.state() != PlannedSessionExecutionPort.SessionState.ASSIGNED) throw new ResponseStatusException(HttpStatus.CONFLICT, "planned session is not available for execution");
        var revision = revisions.findActiveRevisions(participant).stream().filter(item -> item.revisionId().equals(planRevisionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "plan revision is not active for participant"));
        var snapshot = revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream())
                .filter(s -> s.id().equals(plannedSessionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "session does not belong to active revision"));
        String variant = selectedVariantType == null || selectedVariantType.isBlank() ? "STANDARD" : selectedVariantType.trim();
        if (!java.util.Set.of("STANDARD", "SHORT", "MINIMUM").contains(variant)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selected variant is invalid");
        if (!"STANDARD".equals(variant) && snapshot.variants().stream().noneMatch(item -> variant.equals(item.type()))) throw new ResponseStatusException(HttpStatus.CONFLICT, "selected variant is not approved");
        startAuthorization.authorize(participant, plannedSessionId, variant);
        requireSafety(participant, planRevisionId, plannedSessionId);
        SessionExecutionAttempt existing = attempts
                .findFirstByParticipantAccountIdAndPlannedSessionIdOrderByUpdatedAtDesc(participant, plannedSessionId)
                .orElse(null);
        if (existing != null && existing.active()) return view(existing);
        if (executions.findDeclaredSessionIds(participant, java.util.List.of(plannedSessionId)).contains(plannedSessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session already has final execution");
        }
        SessionExecutionAttempt created = attempts.save(new SessionExecutionAttempt(participant, plannedSessionId, planRevisionId, variant, key, clock.instant()));
        metrics.recordForParticipant(participant, "SESSION_ATTEMPT_STARTED", created.id, planRevisionId, plannedSessionId,
                created.id, "SESSION_ATTEMPT_V1", variant);
        adherence.attemptStarted(participant, created.id, plannedSessionId);
        adherence.detect(participant);
        audit.record(subject, "SESSION_ATTEMPT_STARTED", "SessionExecutionAttempt", created.id);
        return view(created);
    }

    @Transactional(readOnly = true)
    public AttemptView active(String subject, UUID plannedSessionId) {
        return attempts.findFirstByParticipantAccountIdAndPlannedSessionIdAndStatusInOrderByUpdatedAtDesc(participant(subject), plannedSessionId,
                java.util.List.of(SessionExecutionAttempt.Status.STARTED.name(), SessionExecutionAttempt.Status.PAUSED.name()))
                .map(SessionExecutionAttemptService::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "active session attempt not found"));
    }

    @Transactional
    public AttemptView pause(String subject, UUID attemptId) { return transition(subject, attemptId, "PAUSE"); }
    @Transactional
    public AttemptView resume(String subject, UUID attemptId) { return transition(subject, attemptId, "RESUME"); }
    @Transactional
    public AttemptView abandon(String subject, UUID attemptId, String reason) { return transition(subject, attemptId, "ABANDON", reason); }

    @Transactional
    public AttemptView updateProgress(String subject, UUID attemptId, UUID prescriptionId, boolean completed) {
        UUID participant = participant(subject);
        SessionExecutionAttempt attempt = owned(participant, attemptId);
        if (!SessionExecutionAttempt.Status.STARTED.name().equals(attempt.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "session attempt is not active");
        requireAllowedPrescription(attempt, prescriptionId);
        Instant now = clock.instant();
        var entry = progress.findByAttemptIdAndExercisePrescriptionId(attemptId, prescriptionId)
                .orElseGet(() -> new SessionExecutionAttemptProgress(attemptId, prescriptionId, completed, now));
        entry.update(completed, now); progress.save(entry); attempt.touch(now);
        audit.record(subject, "SESSION_ATTEMPT_PROGRESS_RECORDED", "SessionExecutionAttempt", attemptId);
        return view(attempt);
    }

    @Transactional
    public AttemptDetailView recordFact(String subject, UUID attemptId, FactCommand command) {
        UUID participant = participant(subject);
        SessionExecutionAttempt attempt = owned(participant, attemptId);
        if (!SessionExecutionAttempt.Status.STARTED.name().equals(attempt.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "session attempt is not active");
        if (command == null || command.exercisePrescriptionId() == null || command.result() == null
                || !java.util.Set.of("PERFORMED", "PARTIAL", "SKIPPED").contains(command.outcome())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fact outcome and result are required");
        }
        if (!command.exercisePrescriptionId().equals(command.result().exercisePrescriptionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fact prescription must match result prescription");
        }
        requireAllowedPrescription(attempt, command.exercisePrescriptionId());
        SessionExecutionService.validateResultValues(command.result());
        PlanRevisionQueryPort.PrescriptionSnapshot prescription = pinnedPrescription(attempt, command.exercisePrescriptionId());
        if (!"SKIPPED".equals(command.outcome()) && !hasPositiveActualWork(command.result(), prescription)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "performed or partial fact requires a positive actual dose");
        }
        if (hasUnsupportedActualWork(command.result(), prescription)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actual dose is not supported by the pinned prescription");
        }
        if (command.result().actualSets() != null && command.result().actualSets() > 1
                && (command.result().actualRepetitions() != null || command.result().actualDurationSeconds() != null
                || command.result().actualContacts() != null) && command.result().actualSetDetails() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "actual set details are required for multi-set exercise dose");
        }
        if (Boolean.TRUE.equals(command.result().skipped()) != "SKIPPED".equals(command.outcome())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skipped outcome must match result");
        }
        if (("PARTIAL".equals(command.outcome()) || "SKIPPED".equals(command.outcome()))
                && (command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 500)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partial or skipped fact reason is required");
        }
        try {
            var previous = facts.findFirstByAttemptIdAndExercisePrescriptionIdOrderByRevisionNumberDesc(attemptId, command.exercisePrescriptionId());
            facts.saveAndFlush(new SessionExecutionAttemptFact(attemptId, command.exercisePrescriptionId(),
                    previous.map(item -> item.revisionNumber + 1).orElse(1), command.outcome(),
                    objectMapper.writeValueAsString(command), clock.instant()));
            entityManager.lock(attempt, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            entityManager.flush();
        } catch (JacksonException exception) { throw new IllegalStateException("cannot serialize execution fact", exception); }
        catch (DataIntegrityViolationException conflict) {
            if (hasConstraint(conflict, "uq_attempt_execution_fact_revision")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "execution fact was updated concurrently", conflict);
            }
            throw conflict;
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session attempt was updated concurrently", conflict);
        }
        attempt.touch(clock.instant());
        audit.record(subject, "SESSION_ATTEMPT_FACT_RECORDED", "SessionExecutionAttempt", attemptId);
        return detail(attempt);
    }

    @Transactional(readOnly = true)
    public AttemptDetailView get(String subject, UUID attemptId) {
        SessionExecutionAttempt attempt = owned(participant(subject), attemptId);
        return detail(attempt);
    }

    @Transactional(readOnly = true)
    public UUID plannedSessionForCompletion(String subject, UUID attemptId) {
        SessionExecutionAttempt attempt = owned(participant(subject), attemptId);
        if (!attempt.active() && !SessionExecutionAttempt.Status.COMPLETED.name().equals(attempt.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "session attempt is not completable");
        return attempt.plannedSessionId;
    }

    @Transactional
    public SessionExecutionService.ExecutionView finish(String subject, UUID attemptId, String idempotencyKey, FinishCommand command) {
        UUID participant = participant(subject);
        SessionExecutionAttempt attempt = owned(participant, attemptId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var replay = executions.findByParticipantAndIdempotencyKey(participant, idempotencyKey.trim());
            if (replay.isPresent() && replay.get().execution().plannedSessionId().equals(attempt.plannedSessionId)) {
                var saved = replay.get();
                var report = saved.report();
                return new SessionExecutionService.ExecutionView(saved.execution().id(), saved.execution().plannedSessionId(), participant,
                        saved.execution().declaredCompletion(), saved.execution().recordedAt(), report.painLevel(), report.difficultyLevel(),
                        report.techniqueConfidenceLevel(), report.note(), report.sessionRpe(), report.observationMode(), saved.execution().outcome(),
                        saved.execution().stopReason(), List.of(), List.of(), List.of(), List.of());
            }
        }
        if (!attempt.active()) throw new ResponseStatusException(HttpStatus.CONFLICT, "session attempt is terminal");
        if (command == null || !java.util.Set.of("COMPLETE", "STOP").contains(command.intent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "finish intent is required");
        }
        if ("STOP".equals(command.intent()) && validReason(command.stopReason()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stop reason is required");
        }
        var selected = selectedPrescriptionIds(attempt);
        var current = facts.findByAttemptIdOrderByExercisePrescriptionIdAscRevisionNumberDesc(attemptId).stream()
                .collect(java.util.stream.Collectors.toMap(item -> item.exercisePrescriptionId, item -> item,
                        (left, right) -> left.revisionNumber > right.revisionNumber ? left : right));
        if ("COMPLETE".equals(command.intent()) && !current.keySet().containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "every selected prescription must be explicitly recorded before complete");
        }
        java.util.List<FactView> recorded = current.values().stream().map(this::factView).toList();
        String outcome = "STOP".equals(command.intent()) ? "STOPPED" : recorded.stream().allMatch(item -> "PERFORMED".equals(item.outcome())) ? "COMPLETED"
                : recorded.stream().allMatch(item -> "SKIPPED".equals(item.outcome())) ? "SKIPPED" : "PARTIAL";
        var results = recorded.stream().map(FactView::result).toList();
        var finalCommand = new SessionExecutionService.DeclareExecutionCommand("COMPLETED".equals(outcome), results,
                command.painLevel(), command.difficultyLevel(), command.techniqueConfidenceLevel(), command.note(), command.sessionRpe(), "DECLARED");
        SessionExecutionService.ExecutionView view = executionService.declareAttempt(subject, attempt.plannedSessionId, idempotencyKey,
                finalCommand, outcome, "STOP".equals(command.intent()) ? validReason(command.stopReason()) : null,
                attempt.id, selectedPrescriptionReferences(attempt));
        if ("STOPPED".equals(outcome)) attempt.abandon(validReason(command.stopReason()), clock.instant());
        else attempt.complete(clock.instant());
        entityManager.lock(attempt, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return view;
    }

    @Transactional(readOnly = true)
    public void validateCompletionResults(String subject, UUID attemptId,
                                          SessionExecutionService.DeclareExecutionCommand command) {
        SessionExecutionAttempt attempt = owned(participant(subject), attemptId);
        if (command == null || command.results() == null || command.results().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "completion results are required");
        }
        for (SessionExecutionService.ResultCommand result : command.results()) {
            if (result == null || result.exercisePrescriptionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "completion result prescription is required");
            }
            requireAllowedPrescription(attempt, result.exercisePrescriptionId());
        }
    }

    
    @Transactional
    public void completeAfterFinalDeclaration(String subject, UUID participantAccountId, UUID plannedSessionId) {
        attempts.findFirstByParticipantAccountIdAndPlannedSessionIdOrderByUpdatedAtDesc(participantAccountId, plannedSessionId)
                .filter(SessionExecutionAttempt::active).ifPresent(attempt -> {
                    attempt.complete(clock.instant());
                    metrics.recordForParticipant(participantAccountId, "SESSION_COMPLETED", attempt.id, attempt.planRevisionId,
                            plannedSessionId, attempt.id, "SESSION_COMPLETION_V1", attempt.selectedVariantType);
                    audit.record(subject, "SESSION_ATTEMPT_COMPLETED", "SessionExecutionAttempt", attempt.id);
                });
    }

    
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SessionExecutionProgress> findForSessions(UUID participantAccountId,
                                                                 Collection<UUID> plannedSessionIds) {
        if (plannedSessionIds == null || plannedSessionIds.isEmpty()) return Map.of();
        var finals = executions.findDeclaredSessionIds(participantAccountId, plannedSessionIds);
        Map<UUID, SessionExecutionProgress> result = new HashMap<>();
        attempts.findByParticipantAccountIdAndPlannedSessionIdInOrderByUpdatedAtDesc(participantAccountId, plannedSessionIds)
                .forEach(attempt -> result.putIfAbsent(attempt.plannedSessionId, progress(attempt, finals.contains(attempt.plannedSessionId))));
        plannedSessionIds.forEach(id -> result.putIfAbsent(id, new SessionExecutionProgress(id, null,
                finals.contains(id) ? terminalState(id) : ExecutionState.NOT_STARTED,
                finals.contains(id), null, null)));
        return Map.copyOf(result);
    }

    private AttemptView transition(String subject, UUID attemptId, String action) { return transition(subject, attemptId, action, null); }
    private AttemptView transition(String subject, UUID attemptId, String action, String reason) {
        UUID participant = participant(subject);
        SessionExecutionAttempt attempt = owned(participant, attemptId);
        Instant now = clock.instant();
        switch (action) {
            case "PAUSE" -> { if (SessionExecutionAttempt.Status.STARTED.name().equals(attempt.status)) { attempt.pause(now); audit.record(subject, "SESSION_ATTEMPT_PAUSED", "SessionExecutionAttempt", attemptId); } }
            case "RESUME" -> { if (SessionExecutionAttempt.Status.PAUSED.name().equals(attempt.status)) { requireSafety(participant, attempt.planRevisionId, attempt.plannedSessionId); attempt.resume(now); audit.record(subject, "SESSION_ATTEMPT_RESUMED", "SessionExecutionAttempt", attemptId); } }
            case "ABANDON" -> { if (attempt.active()) { attempt.abandon(validReason(reason), now); audit.record(subject, "SESSION_ATTEMPT_ABANDONED", "SessionExecutionAttempt", attemptId); } }
            default -> throw new IllegalArgumentException(action);
        }
        if ("ABANDON".equals(action)) adherence.detect(participant);
        return view(attempt);
    }

    private SessionExecutionAttempt owned(UUID participant, UUID attemptId) {
        return attempts.findById(attemptId).filter(item -> participant.equals(item.participantAccountId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session attempt not found"));
    }
    private AttemptDetailView detail(SessionExecutionAttempt attempt) {
        return new AttemptDetailView(attempt.id, attempt.plannedSessionId, attempt.planRevisionId, attempt.selectedVariantType,
                attempt.status, attempt.startedAt, attempt.lastActivityAt, attempt.abandonmentReason,
                progress.findByAttemptIdOrderByUpdatedAt(attempt.id).stream().map(item -> new ProgressView(item.exercisePrescriptionId, item.completed, item.updatedAt)).toList(),
                facts.findByAttemptIdOrderByExercisePrescriptionIdAscRevisionNumberDesc(attempt.id).stream()
                        .collect(java.util.stream.Collectors.groupingBy(item -> item.exercisePrescriptionId,
                                java.util.LinkedHashMap::new, java.util.stream.Collectors.maxBy(java.util.Comparator.comparingInt(item -> item.revisionNumber))))
                        .values().stream().flatMap(java.util.Optional::stream).map(this::factView).toList(), sessionSnapshot(attempt));
    }
    private FactView factView(SessionExecutionAttemptFact item) {
        try { FactCommand command = objectMapper.readValue(item.resultPayload, FactCommand.class);
            return new FactView(item.exercisePrescriptionId, item.revisionNumber, item.outcome, command.reason(), command.result(), item.recordedAt); }
        catch (JacksonException exception) { throw new IllegalStateException("stored execution fact is invalid", exception); }
    }
    private PlanRevisionQueryPort.SessionSnapshot sessionSnapshot(SessionExecutionAttempt attempt) {
        return revisions.findRevision(attempt.planRevisionId).flatMap(revision -> revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream()).filter(session -> session.id().equals(attempt.plannedSessionId)).findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "attempt session is unavailable"));
    }
    private void requireSafety(UUID participant, UUID revisionId, UUID sessionId) {
        var decision = safety.evaluateForSessions(participant, revisionId, java.util.List.of(sessionId), clock.instant()).get(sessionId);
        if (decision == null || decision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.NOT_ASSESSED || decision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.BLOCKED) throw new ResponseStatusException(HttpStatus.CONFLICT, "current safety assessment does not permit execution");
    }
    private void requireAllowedPrescription(SessionExecutionAttempt attempt, UUID prescriptionId) {
        var revision = revisions.findRevision(attempt.planRevisionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "attempt revision is unavailable"));
        var session = revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream()).filter(s -> s.id().equals(attempt.plannedSessionId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "attempt session is unavailable"));
        java.util.Set<UUID> allowed = selectedPrescriptionIds(attempt);
        if (!allowed.contains(prescriptionId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prescription is not part of selected variant");
    }
    private PlanRevisionQueryPort.PrescriptionSnapshot pinnedPrescription(SessionExecutionAttempt attempt, UUID prescriptionId) {
        return sessionSnapshot(attempt).prescriptions().stream().filter(item -> item.id().equals(prescriptionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "attempt prescription is unavailable"));
    }
    private java.util.Set<UUID> selectedPrescriptionIds(SessionExecutionAttempt attempt) {
        var session = sessionSnapshot(attempt);
        return "STANDARD".equals(attempt.selectedVariantType) ? session.prescriptions().stream().map(PlanRevisionQueryPort.PrescriptionSnapshot::id).collect(java.util.stream.Collectors.toSet()) : session.variants().stream().filter(v -> attempt.selectedVariantType.equals(v.type())).findFirst().map(v -> v.items().stream().map(PlanRevisionQueryPort.SessionVariantItemSnapshot::basePrescriptionId).collect(java.util.stream.Collectors.toSet())).orElse(java.util.Set.of());
    }
    private List<SessionExecutionService.PrescriptionReference> selectedPrescriptionReferences(SessionExecutionAttempt attempt) {
        var session = sessionSnapshot(attempt);
        var allowed = selectedPrescriptionIds(attempt);
        return session.prescriptions().stream().filter(item -> allowed.contains(item.id()))
                .map(item -> new SessionExecutionService.PrescriptionReference(item.id(), item.exerciseVersionId())).toList();
    }
    private ExecutionState terminalState(UUID plannedSessionId) {
        return executions.findByPlannedSessionId(plannedSessionId).map(item -> switch (item.execution().outcome()) {
            case "PARTIAL" -> ExecutionState.PARTIAL;
            case "SKIPPED" -> ExecutionState.SKIPPED;
            case "STOPPED" -> ExecutionState.STOPPED;
            default -> ExecutionState.COMPLETED;
        }).orElse(ExecutionState.COMPLETED);
    }
    private static String requiredKey(String key) { if (key == null || key.isBlank() || key.trim().length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required"); return key.trim(); }
    private static boolean hasPositiveActualWork(SessionExecutionService.ResultCommand result, PlanRevisionQueryPort.PrescriptionSnapshot prescription) {
        return (allowsRepetitions(prescription) && (positive(result.actualRepetitions()) || setPositive(result, SessionExecutionService.ActualSet::repetitions)))
                || (allowsDuration(prescription) && (positive(result.actualDurationSeconds()) || setPositive(result, SessionExecutionService.ActualSet::durationSeconds)))
                || (allowsContacts(prescription) && (positive(result.actualContacts()) || setPositive(result, SessionExecutionService.ActualSet::contacts)))
                || (allowsDistance(prescription) && positive(result.actualDistanceMeters()));
    }
    private static boolean hasUnsupportedActualWork(SessionExecutionService.ResultCommand result, PlanRevisionQueryPort.PrescriptionSnapshot prescription) {
        return (!allowsRepetitions(prescription) && (positive(result.actualRepetitions()) || setPositive(result, SessionExecutionService.ActualSet::repetitions)))
                || (!allowsDuration(prescription) && (positive(result.actualDurationSeconds()) || setPositive(result, SessionExecutionService.ActualSet::durationSeconds)))
                || (!allowsContacts(prescription) && (positive(result.actualContacts()) || setPositive(result, SessionExecutionService.ActualSet::contacts)))
                || (!allowsDistance(prescription) && positive(result.actualDistanceMeters()));
    }
    private static boolean allowsRepetitions(PlanRevisionQueryPort.PrescriptionSnapshot item) { return switch (item.doseType()) { case "DYNAMIC_RESISTANCE" -> true; case "MOBILITY_CONTROL" -> item.repetitions() != null; default -> legacy(item) && item.repetitions() != null; }; }
    private static boolean allowsDuration(PlanRevisionQueryPort.PrescriptionSnapshot item) { return switch (item.doseType()) { case "ISOMETRIC" -> true; case "ENDURANCE", "MOBILITY_CONTROL" -> item.durationSeconds() != null; default -> legacy(item) && item.durationSeconds() != null; }; }
    private static boolean allowsContacts(PlanRevisionQueryPort.PrescriptionSnapshot item) { return "IMPACT".equals(item.doseType()) || legacy(item) && item.contacts() != null; }
    private static boolean allowsDistance(PlanRevisionQueryPort.PrescriptionSnapshot item) { return "ENDURANCE".equals(item.doseType()) && item.distanceMeters() != null || legacy(item) && item.distanceMeters() != null; }
    private static boolean legacy(PlanRevisionQueryPort.PrescriptionSnapshot item) { return item.doseType() == null || "LEGACY_UNTYPED".equals(item.doseType()); }
    private static boolean setPositive(SessionExecutionService.ResultCommand result, java.util.function.Function<SessionExecutionService.ActualSet, Integer> value) { return result.actualSetDetails() != null && result.actualSetDetails().stream().anyMatch(item -> item != null && positive(value.apply(item))); }
    private static boolean positive(Integer value) { return value != null && value > 0; }
    private static boolean positive(java.math.BigDecimal value) { return value != null && value.signum() > 0; }
    private static boolean hasConstraint(Throwable error, String name) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) return true;
        }
        return false;
    }
    private static String validReason(String reason) { if (reason == null || reason.isBlank()) return null; String value = reason.trim(); if (!java.util.Set.of("TIME", "PAIN_OR_SYMPTOMS", "TOO_DIFFICULT", "FATIGUE", "ILLNESS", "OTHER").contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abandonment reason is invalid"); return value; }

    private UUID participant(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        }
        return participants.findParticipantIdByPrincipalAccountId(account.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "an active participant access link is required"));
    }

    private SessionExecutionProgress progress(SessionExecutionAttempt attempt, boolean finalDeclared) {
        ExecutionState state = finalDeclared ? terminalState(attempt.plannedSessionId) : switch (SessionExecutionAttempt.Status.valueOf(attempt.status)) {
            case STARTED -> ExecutionState.IN_PROGRESS;
            case PAUSED -> ExecutionState.PAUSED;
            case COMPLETED -> ExecutionState.COMPLETED;
            case ABANDONED -> ExecutionState.ABANDONED;
        };
        return new SessionExecutionProgress(attempt.plannedSessionId, attempt.active() ? attempt.id : null,
                state, finalDeclared, attempt.startedAt, attempt.completedAt);
    }

    public record AttemptView(UUID attemptId, UUID plannedSessionId, UUID planRevisionId, String selectedVariantType, String state, Instant lastActivityAt, Instant updatedAt) { }
    public record ProgressView(UUID exercisePrescriptionId, boolean completed, Instant updatedAt) { }
    public record FactCommand(UUID exercisePrescriptionId, String outcome, String reason, SessionExecutionService.ResultCommand result) { }
    public record FinishCommand(String intent, int painLevel, int difficultyLevel, Integer techniqueConfidenceLevel,
                                String note, Integer sessionRpe, String stopReason) { }
    public record FactView(UUID exercisePrescriptionId, int revisionNumber, String outcome, String reason, SessionExecutionService.ResultCommand result, Instant recordedAt) { }
    public record AttemptDetailView(UUID attemptId, UUID plannedSessionId, UUID planRevisionId, String selectedVariantType, String state, Instant startedAt, Instant lastActivityAt, String abandonmentReason, java.util.List<ProgressView> progress, java.util.List<FactView> facts, PlanRevisionQueryPort.SessionSnapshot session) { }
    private static AttemptView view(SessionExecutionAttempt attempt) {
        return new AttemptView(attempt.id, attempt.plannedSessionId, attempt.planRevisionId, attempt.selectedVariantType, attempt.status, attempt.lastActivityAt, attempt.updatedAt);
    }
}
