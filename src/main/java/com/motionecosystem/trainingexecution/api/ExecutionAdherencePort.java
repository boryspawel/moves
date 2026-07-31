package com.motionecosystem.trainingexecution.api;

import java.util.UUID;

/** Callback for adherence recovery state updates caused by execution lifecycle events. */
public interface ExecutionAdherencePort {
    void attemptStarted(UUID participantAccountId, UUID attemptId, UUID plannedSessionId);

    void executionCompleted(UUID participantAccountId, UUID plannedSessionId, UUID executionId);

    void detect(UUID participantAccountId);
}
