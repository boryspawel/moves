package com.motionecosystem.notification.reminders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reminder_delivery", schema = "notification")
class ReminderDelivery {
    @Id UUID id;
    @Column(name = "participant_account_id") UUID participantAccountId;
    @Column(name = "planned_session_id") UUID plannedSessionId;
    @Column(name = "reason_code") String reasonCode;
    @Column(name = "rule_version_code") String ruleVersionCode;
    @Column(name = "local_delivery_date") LocalDate localDeliveryDate;
    String channel;
    @Column(name = "idempotency_key") String idempotencyKey;
    String decision;
    @Column(name = "decided_at") Instant decidedAt;
    @Column(name = "delivered_at") Instant deliveredAt;
    protected ReminderDelivery() { }
    ReminderDelivery(UUID participant, UUID session, String reason, String version, LocalDate date, String channel, String key, boolean deliver, Instant at) {
        id = UUID.randomUUID(); participantAccountId = participant; plannedSessionId = session; reasonCode = reason; ruleVersionCode = version;
        localDeliveryDate = date; this.channel = channel; idempotencyKey = key; decision = deliver ? "DELIVERED" : "SUPPRESSED"; decidedAt = at; deliveredAt = deliver ? at : null;
    }
}
