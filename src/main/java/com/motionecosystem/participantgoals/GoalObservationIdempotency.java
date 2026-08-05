package com.motionecosystem.participantgoals;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goal_observation_idempotency", schema = "participant_goals", uniqueConstraints = @UniqueConstraint(columnNames = {"specialist_account_id", "operation", "idempotency_key"}))
class GoalObservationIdempotency {
    @Id UUID id;
    @Column(name = "specialist_account_id", nullable = false) UUID specialistAccountId;
    @Column(nullable = false, length = 96) String operation;
    @Column(name = "idempotency_key", nullable = false, length = 120) String idempotencyKey;
    @Column(name = "observation_id", nullable = false) UUID observationId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected GoalObservationIdempotency() { }
    GoalObservationIdempotency(UUID specialist, String operation, String key, UUID observationId, Instant now) { id = UUID.randomUUID(); specialistAccountId = specialist; this.operation = operation; idempotencyKey = key; this.observationId = observationId; createdAt = now; }
}
