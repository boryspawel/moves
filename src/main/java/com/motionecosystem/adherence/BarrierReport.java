package com.motionecosystem.adherence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "barrier_report", schema = "adherence")
class BarrierReport {
    @Id UUID id;
    @Column(name = "participant_account_id") UUID participantAccountId;
    @Column(name = "planned_session_id") UUID plannedSessionId;
    @Column(name = "session_attempt_id") UUID sessionAttemptId;
    @Column(name = "plan_revision_id") UUID planRevisionId;
    String category;
    @Column(name = "rule_version_code") String ruleVersionCode;
    @Column(name = "proposed_options") String proposedOptions;
    @Column(name = "selected_action") String selectedAction;
    @Column(name = "action_outcome") String actionOutcome;
    @Column(name = "idempotency_key") String idempotencyKey;
    @Column(name = "reported_at") Instant reportedAt;
    protected BarrierReport() { }
    BarrierReport(UUID participantAccountId, UUID plannedSessionId, UUID sessionAttemptId, UUID planRevisionId,
                  String category, String proposedOptions, String selectedAction, String actionOutcome,
                  String idempotencyKey, Instant reportedAt) {
        this.id = UUID.randomUUID(); this.participantAccountId = participantAccountId; this.plannedSessionId = plannedSessionId;
        this.sessionAttemptId = sessionAttemptId; this.planRevisionId = planRevisionId; this.category = category;
        this.ruleVersionCode = "BARRIER_RESPONSE_V1"; this.proposedOptions = proposedOptions;
        this.selectedAction = selectedAction; this.actionOutcome = actionOutcome;
        this.idempotencyKey = idempotencyKey; this.reportedAt = reportedAt;
    }
}
