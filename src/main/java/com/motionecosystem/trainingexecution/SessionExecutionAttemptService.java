package com.motionecosystem.trainingexecution;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort;
import com.motionecosystem.trainingexecution.api.SessionStartAuthorizationPort;
import com.motionecosystem.adherence.RecoveryEpisodeService;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import com.motionecosystem.trainingplanning.api.PlannedSessionExecutionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SessionExecutionAttemptService implements SessionExecutionProgressQueryPort {
    private final CurrentAccountService accounts;
    private final PlannedSessionExecutionPort sessions;
    private final SessionExecutionAttemptRepository attempts;
    private final SessionExecutionAttemptProgressRepository progress;
    private final SessionExecutionPersistence executions;
    private final PlanRevisionQueryPort revisions;
    private final SessionSafetyDecisionQueryPort safety;
    private final SessionStartAuthorizationPort startAuthorization;
    private final RecoveryEpisodeService recovery;
    private final AuditRecorder audit;
    private final Clock clock;

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
        var revision = revisions.findActiveRevision(participant).filter(item -> item.revisionId().equals(planRevisionId))
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
        recovery.attemptStarted(participant, created.id, plannedSessionId);
        recovery.detect(participant);
        audit.record(subject, "SESSION_ATTEMPT_STARTED", "SessionExecutionAttempt", created.id);
        return view(created);
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

    @Transactional(readOnly = true)
    public AttemptDetailView get(String subject, UUID attemptId) {
        SessionExecutionAttempt attempt = owned(participant(subject), attemptId);
        return new AttemptDetailView(attempt.id, attempt.plannedSessionId, attempt.planRevisionId, attempt.selectedVariantType,
                attempt.status, attempt.startedAt, attempt.lastActivityAt, attempt.abandonmentReason,
                progress.findByAttemptIdOrderByUpdatedAt(attemptId).stream()
                        .map(item -> new ProgressView(item.exercisePrescriptionId, item.completed, item.updatedAt)).toList());
    }

    @Transactional(readOnly = true)
    public UUID plannedSessionForCompletion(String subject, UUID attemptId) {
        SessionExecutionAttempt attempt = owned(participant(subject), attemptId);
        if (!attempt.active() && !SessionExecutionAttempt.Status.COMPLETED.name().equals(attempt.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "session attempt is not completable");
        return attempt.plannedSessionId;
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
                finals.contains(id) ? ExecutionState.COMPLETED : ExecutionState.NOT_STARTED,
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
        if ("ABANDON".equals(action)) recovery.detect(participant);
        return view(attempt);
    }

    private SessionExecutionAttempt owned(UUID participant, UUID attemptId) {
        return attempts.findById(attemptId).filter(item -> participant.equals(item.participantAccountId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session attempt not found"));
    }
    private void requireSafety(UUID participant, UUID revisionId, UUID sessionId) {
        var decision = safety.evaluateForSessions(participant, revisionId, java.util.List.of(sessionId), clock.instant()).get(sessionId);
        if (decision == null || decision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.NOT_ASSESSED || decision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.BLOCKED) throw new ResponseStatusException(HttpStatus.CONFLICT, "current safety assessment does not permit execution");
    }
    private void requireAllowedPrescription(SessionExecutionAttempt attempt, UUID prescriptionId) {
        var revision = revisions.findRevision(attempt.planRevisionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "attempt revision is unavailable"));
        var session = revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream()).filter(s -> s.id().equals(attempt.plannedSessionId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "attempt session is unavailable"));
        java.util.Set<UUID> allowed = "STANDARD".equals(attempt.selectedVariantType) ? session.prescriptions().stream().map(PlanRevisionQueryPort.PrescriptionSnapshot::id).collect(java.util.stream.Collectors.toSet()) : session.variants().stream().filter(v -> attempt.selectedVariantType.equals(v.type())).findFirst().map(v -> v.items().stream().map(PlanRevisionQueryPort.SessionVariantItemSnapshot::basePrescriptionId).collect(java.util.stream.Collectors.toSet())).orElse(java.util.Set.of());
        if (!allowed.contains(prescriptionId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prescription is not part of selected variant");
    }
    private static String requiredKey(String key) { if (key == null || key.isBlank() || key.trim().length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required"); return key.trim(); }
    private static String validReason(String reason) { if (reason == null || reason.isBlank()) return null; String value = reason.trim(); if (!java.util.Set.of("TIME", "PAIN_OR_SYMPTOMS", "TOO_DIFFICULT", "FATIGUE", "ILLNESS", "OTHER").contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abandonment reason is invalid"); return value; }

    private UUID participant(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        }
        return account.id();
    }

    private static SessionExecutionProgress progress(SessionExecutionAttempt attempt, boolean finalDeclared) {
        ExecutionState state = finalDeclared ? ExecutionState.COMPLETED : switch (SessionExecutionAttempt.Status.valueOf(attempt.status)) {
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
    public record AttemptDetailView(UUID attemptId, UUID plannedSessionId, UUID planRevisionId, String selectedVariantType, String state, Instant startedAt, Instant lastActivityAt, String abandonmentReason, java.util.List<ProgressView> progress) { }
    private static AttemptView view(SessionExecutionAttempt attempt) {
        return new AttemptView(attempt.id, attempt.plannedSessionId, attempt.planRevisionId, attempt.selectedVariantType, attempt.status, attempt.lastActivityAt, attempt.updatedAt);
    }
}
