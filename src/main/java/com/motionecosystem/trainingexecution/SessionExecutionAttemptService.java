package com.motionecosystem.trainingexecution;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort;
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
    private final SessionExecutionPersistence executions;
    private final Clock clock;

    @Transactional
    public AttemptView start(String subject, UUID plannedSessionId, UUID planRevisionId) {
        UUID participant = participant(subject);
        sessions.findSession(plannedSessionId).filter(session -> participant.equals(session.participantAccountId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "planned session not found"));
        SessionExecutionAttempt existing = attempts
                .findFirstByParticipantAccountIdAndPlannedSessionIdOrderByUpdatedAtDesc(participant, plannedSessionId)
                .orElse(null);
        if (existing != null && existing.active()) return view(existing);
        if (executions.findDeclaredSessionIds(participant, java.util.List.of(plannedSessionId)).contains(plannedSessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "session already has final execution");
        }
        return view(attempts.save(new SessionExecutionAttempt(participant, plannedSessionId, planRevisionId, clock.instant())));
    }

    @Transactional
    public AttemptView pause(String subject, UUID attemptId) { return transition(subject, attemptId, "PAUSE"); }
    @Transactional
    public AttemptView resume(String subject, UUID attemptId) { return transition(subject, attemptId, "RESUME"); }
    @Transactional
    public AttemptView abandon(String subject, UUID attemptId) { return transition(subject, attemptId, "ABANDON"); }

    
    @Transactional
    public void completeAfterFinalDeclaration(UUID participantAccountId, UUID plannedSessionId) {
        attempts.findFirstByParticipantAccountIdAndPlannedSessionIdOrderByUpdatedAtDesc(participantAccountId, plannedSessionId)
                .filter(SessionExecutionAttempt::active).ifPresent(attempt -> attempt.complete(clock.instant()));
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

    private AttemptView transition(String subject, UUID attemptId, String action) {
        UUID participant = participant(subject);
        SessionExecutionAttempt attempt = attempts.findById(attemptId)
                .filter(item -> participant.equals(item.participantAccountId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session attempt not found"));
        Instant now = clock.instant();
        switch (action) {
            case "PAUSE" -> { if (SessionExecutionAttempt.Status.STARTED.name().equals(attempt.status)) attempt.pause(now); }
            case "RESUME" -> { if (SessionExecutionAttempt.Status.PAUSED.name().equals(attempt.status)) attempt.resume(now); }
            case "ABANDON" -> { if (attempt.active()) attempt.abandon(now); }
            default -> throw new IllegalArgumentException(action);
        }
        return view(attempt);
    }

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

    public record AttemptView(UUID attemptId, UUID plannedSessionId, String state, Instant updatedAt) { }
    private static AttemptView view(SessionExecutionAttempt attempt) {
        return new AttemptView(attempt.id, attempt.plannedSessionId, attempt.status, attempt.updatedAt);
    }
}
