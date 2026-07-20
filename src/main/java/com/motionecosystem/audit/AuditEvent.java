package com.motionecosystem.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_event", schema = "audit")
class AuditEvent {

    @Id
    private UUID id;
    @Column(name = "actor_subject", nullable = false)
    private String actorSubject;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "aggregate_type")
    private String aggregateType;
    @Column(name = "aggregate_id")
    private UUID aggregateId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    AuditEvent(String actorSubject, String eventType, String aggregateType, UUID aggregateId, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.actorSubject = actorSubject;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
    }
}
