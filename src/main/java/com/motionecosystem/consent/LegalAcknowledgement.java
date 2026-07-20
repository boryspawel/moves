package com.motionecosystem.consent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "legal_acknowledgement", schema = "consent")
class LegalAcknowledgement {

    @Id
    UUID id;
    @Column(name = "account_id", nullable = false)
    UUID accountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "acknowledgement_type", nullable = false)
    AcknowledgementType type;
    @Column(name = "document_version", nullable = false)
    String documentVersion;
    @Column(name = "accepted_at", nullable = false)
    Instant acceptedAt;

    protected LegalAcknowledgement() {
    }

    LegalAcknowledgement(UUID accountId, AcknowledgementType type, String version, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.type = type;
        this.documentVersion = version;
        this.acceptedAt = now;
    }
}
