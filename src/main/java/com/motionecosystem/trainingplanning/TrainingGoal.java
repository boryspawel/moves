package com.motionecosystem.trainingplanning;

import java.time.Instant;
import java.util.UUID;

public record TrainingGoal(UUID id, UUID participantAccountId, String name,
                           UUID createdByAccountId, Instant createdAt) {
}
