package com.motionecosystem.trainingexecution.api;

import java.util.Optional;
import java.util.UUID;

/** Read-only attempt boundary used by adherence without exposing execution persistence. */
public interface SessionExecutionAttemptQueryPort {
    Optional<AttemptSnapshot> findOwnedAttempt(UUID participantAccountId, UUID attemptId);

    record AttemptSnapshot(UUID attemptId, UUID participantAccountId, UUID plannedSessionId,
                           UUID planRevisionId, String state) {
        public boolean active() { return "STARTED".equals(state) || "PAUSED".equals(state); }
    }
}
