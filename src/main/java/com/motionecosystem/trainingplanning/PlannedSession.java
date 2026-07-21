package com.motionecosystem.trainingplanning;

import java.time.Instant;
import java.util.UUID;

public record PlannedSession(UUID id, UUID microcycleId, UUID participantAccountId,
                             String title, SessionKind kind, SessionStatus status,
                             Instant assignedAt) {

    public enum SessionKind { SELF_GUIDED, OFFLINE_APPOINTMENT }
    public enum SessionStatus { DRAFT, ASSIGNED, COMPLETED, CANCELLED }
}
