package com.motionecosystem.participantgoals;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goal_idempotency", schema = "participant_goals", uniqueConstraints = @UniqueConstraint(columnNames = {"specialist_account_id", "operation", "idempotency_key"}))
class GoalIdempotency {
    @Id UUID id; @Column(name = "specialist_account_id", nullable = false) UUID specialistAccountId; @Column(nullable = false, length = 64) String operation; @Column(name = "idempotency_key", nullable = false, length = 120) String idempotencyKey; @Column(name = "goal_id", nullable = false) UUID goalId; @Column(name = "created_at", nullable = false) Instant createdAt;
    protected GoalIdempotency() { }
    GoalIdempotency(UUID specialistId, String operation, String key, UUID goalId, Instant now) { id = UUID.randomUUID(); specialistAccountId = specialistId; this.operation = operation; idempotencyKey = key; this.goalId = goalId; createdAt = now; }
}
