package com.motionecosystem.trainingexecution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_execution_attempt", schema = "training_execution")
class SessionExecutionAttempt {
    enum Status { STARTED, PAUSED, COMPLETED, ABANDONED }

    @Id UUID id;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(name = "planned_session_id", nullable = false) UUID plannedSessionId;
    @Column(name = "plan_revision_id") UUID planRevisionId;
    @Column(name = "selected_variant_type", nullable = false) String selectedVariantType;
    @Column(name = "start_idempotency_key", nullable = false, length = 120) String startIdempotencyKey;
    @Column(name = "last_activity_at", nullable = false) Instant lastActivityAt;
    @Column(name = "abandonment_reason") String abandonmentReason;
    @Column(nullable = false) String status;
    @Column(name = "started_at", nullable = false) Instant startedAt;
    @Column(name = "paused_at") Instant pausedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "abandoned_at") Instant abandonedAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version long version;

    protected SessionExecutionAttempt() { }

    SessionExecutionAttempt(UUID participantAccountId, UUID plannedSessionId, UUID planRevisionId,
                            String selectedVariantType, String startIdempotencyKey, Instant now) {
        id = UUID.randomUUID(); this.participantAccountId = participantAccountId;
        this.plannedSessionId = plannedSessionId; this.planRevisionId = planRevisionId;
        this.selectedVariantType = selectedVariantType; this.startIdempotencyKey = startIdempotencyKey;
        status = Status.STARTED.name(); startedAt = now; lastActivityAt = now; updatedAt = now;
    }

    boolean active() { return Status.STARTED.name().equals(status) || Status.PAUSED.name().equals(status); }
    void pause(Instant now) { status = Status.PAUSED.name(); pausedAt = now; lastActivityAt = now; updatedAt = now; }
    void resume(Instant now) { status = Status.STARTED.name(); pausedAt = null; lastActivityAt = now; updatedAt = now; }
    void complete(Instant now) { status = Status.COMPLETED.name(); completedAt = now; lastActivityAt = now; updatedAt = now; }
    void touch(Instant now) { lastActivityAt = now; updatedAt = now; }
    void abandon(String reason, Instant now) { status = Status.ABANDONED.name(); abandonmentReason = reason; abandonedAt = now; lastActivityAt = now; updatedAt = now; }
}
