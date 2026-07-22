package com.motionecosystem.analytics.adherencemetrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adherence_metric_event", schema = "analytics")
class AdherenceMetricEvent {
    @Id UUID id;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(name = "event_code", nullable = false) String eventCode;
    @Column(name = "technical_reference_id") UUID technicalReferenceId;
    @Column(name = "plan_revision_id") UUID planRevisionId;
    @Column(name = "planned_session_id") UUID plannedSessionId;
    @Column(name = "session_attempt_id") UUID sessionAttemptId;
    @Column(name = "rule_version_code") String ruleVersionCode;
    @Column(name = "variant_code") String variantCode;
    @Column(name = "deduplication_key", nullable = false) String deduplicationKey;
    @Column(name = "occurred_at", nullable = false) Instant occurredAt;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;

    protected AdherenceMetricEvent() { }
    AdherenceMetricEvent(UUID participant, String eventCode, UUID reference, UUID planRevisionId,
                         UUID plannedSessionId, UUID sessionAttemptId, String ruleVersionCode,
                         String variantCode, String deduplicationKey, Instant occurredAt, Instant expiresAt) {
        id = UUID.randomUUID(); participantAccountId = participant; this.eventCode = eventCode;
        technicalReferenceId = reference; this.planRevisionId = planRevisionId; this.plannedSessionId = plannedSessionId;
        this.sessionAttemptId = sessionAttemptId; this.ruleVersionCode = ruleVersionCode; this.variantCode = variantCode;
        this.deduplicationKey = deduplicationKey; this.occurredAt = occurredAt; this.expiresAt = expiresAt;
    }
}
