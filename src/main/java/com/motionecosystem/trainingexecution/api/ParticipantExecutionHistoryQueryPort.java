package com.motionecosystem.trainingexecution.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded append-only execution and attempt history for adherence rules. */
public interface ParticipantExecutionHistoryQueryPort {
    List<ExecutionStart> starts(UUID participantAccountId, Instant fromInclusive, Instant toExclusive, int limit);
    /** Seek page ordered by effective occurrence, recording time, then immutable attempt id. */
    List<ExecutionStart> timeline(UUID participantAccountId, Instant fromInclusive, Instant toExclusive,
                                  SeekCursor after, int limit);
    record SeekCursor(Instant effectiveFrom, Instant recordedAt, UUID stableId) { }
    record ExecutionStart(UUID attemptId, UUID plannedSessionId, UUID planRevisionId, String state, String variant,
                          Instant startedAt, Instant completedAt, Instant abandonedAt, Instant updatedAt,
                          String abandonmentReason) { }
}
