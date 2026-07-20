package com.motionecosystem.trainingexecution;

import java.time.Instant;
import java.util.UUID;

public record PainDifficultyReport(UUID id, UUID sessionExecutionId, int painLevel,
                                   int difficultyLevel, String note, Instant reportedAt) {
}
