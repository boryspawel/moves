package com.motionecosystem.trainingexecution;

import java.time.Instant;
import java.util.UUID;

public record SessionExecution(UUID id, UUID plannedSessionId, UUID participantAccountId,
                               boolean declaredCompletion, String idempotencyKey,
                               Instant recordedAt) {
}
