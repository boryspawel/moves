package com.motionecosystem.specialist;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "client_create_idempotency", schema = "specialist")
class ClientCreateIdempotency {
    @EmbeddedId Id id; @Column(name = "participant_id", nullable = false) UUID participantId; @Column(name = "created_at", nullable = false) Instant createdAt;
    protected ClientCreateIdempotency() { }
    ClientCreateIdempotency(UUID specialistId, UUID key, UUID participantId, Instant createdAt) { id = new Id(specialistId, key); this.participantId = participantId; this.createdAt = createdAt; }
    UUID participantId() { return participantId; }
    @Embeddable static class Id implements java.io.Serializable { @Column(name = "specialist_id") UUID specialistId; @Column(name = "idempotency_key") UUID idempotencyKey; protected Id() { } Id(UUID specialistId, UUID idempotencyKey) { this.specialistId=specialistId; this.idempotencyKey=idempotencyKey; } }
}
