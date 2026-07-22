package com.motionecosystem.trainingexecution.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface SessionExecutionProgressQueryPort {

    Map<UUID, SessionExecutionProgress> findForSessions(
            UUID participantAccountId, Collection<UUID> plannedSessionIds);

    enum ExecutionState { NOT_STARTED, IN_PROGRESS, PAUSED, COMPLETED, ABANDONED }

    record SessionExecutionProgress(UUID plannedSessionId, UUID activeAttemptId,
                                    ExecutionState state, boolean finalExecutionDeclared,
                                    Instant startedAt, Instant completedAt) { }
}
