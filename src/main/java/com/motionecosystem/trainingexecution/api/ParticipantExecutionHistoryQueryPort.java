package com.motionecosystem.trainingexecution.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded append-only execution and attempt history for adherence rules. */
public interface ParticipantExecutionHistoryQueryPort {
    List<ExecutionStart> starts(UUID participantAccountId, Instant fromInclusive, Instant toExclusive, int limit);
    record ExecutionStart(UUID attemptId, String state, String variant, Instant startedAt, Instant completedAt, Instant abandonedAt, String abandonmentReason) { }
}
