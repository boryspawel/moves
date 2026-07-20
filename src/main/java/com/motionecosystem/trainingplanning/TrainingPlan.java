package com.motionecosystem.trainingplanning;

import java.time.Instant;
import java.util.UUID;

public record TrainingPlan(UUID id, UUID goalId, UUID participantAccountId,
                           UUID createdByAccountId, String name, PlanMode mode,
                           PlanStatus status, Instant createdAt) {

    public enum PlanMode { SPECIALIST_ASSIGNED, SELF_DIRECTED }
    public enum PlanStatus { ACTIVE, ARCHIVED }
}
