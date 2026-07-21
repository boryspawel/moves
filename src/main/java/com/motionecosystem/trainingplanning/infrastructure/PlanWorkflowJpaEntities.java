package com.motionecosystem.trainingplanning.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "PlanWarningAcknowledgementJpaEntity")
@Table(name = "plan_warning_acknowledgement", schema = "training_planning")
class PlanWarningAcknowledgementJpaEntity {
    @Id UUID id;
    @Column(name = "revision_id") UUID revisionId;
    @Column(name = "assessment_id") UUID assessmentId;
    @Column(name = "factor_id") UUID factorId;
    @Column(name = "actor_account_id") UUID actorId;
    @Column(name = "actor_capability") String actorCapability;
    String rationale;
    @Column(name = "acknowledged_at") Instant acknowledgedAt;

    protected PlanWarningAcknowledgementJpaEntity() {
    }
}

@Entity(name = "PlanActivationRequestJpaEntity")
@Table(name = "plan_activation_request", schema = "training_planning")
class PlanActivationRequestJpaEntity {
    @Id UUID id;
    @Column(name = "revision_id") UUID revisionId;
    @Column(name = "idempotency_key") String idempotencyKey;
    @Column(name = "actor_account_id") UUID actorId;
    @Column(name = "activated_at") Instant activatedAt;

    protected PlanActivationRequestJpaEntity() {
    }
}
