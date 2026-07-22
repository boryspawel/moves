package com.motionecosystem.trainingexecution.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Minimal append-only completion history exposed to adherence. */
public interface ExecutionHistoryQueryPort {
    Optional<Instant> latestDeclaredCompletion(UUID participantAccountId);
}
