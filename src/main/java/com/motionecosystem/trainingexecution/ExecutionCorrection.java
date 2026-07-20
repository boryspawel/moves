package com.motionecosystem.trainingexecution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionCorrection(UUID id, UUID sessionExecutionId, UUID correctedByAccountId,
                                  String reason, Integer correctedPainLevel,
                                  Integer correctedDifficultyLevel, Instant correctedAt) {
}
