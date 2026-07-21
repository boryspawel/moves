package com.motionecosystem.specialist;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "professional_scope", schema = "specialist")
class ProfessionalScope {

    enum VerificationStatus {
        PENDING,
        VERIFIED,
        REJECTED,
        SUSPENDED
    }

    @EmbeddedId
    Id id;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    VerificationStatus status;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @Column(name = "created_at")
    Instant createdAt;

    protected ProfessionalScope() {
    }

    ProfessionalScope(UUID accountId, SpecialistKind kind, Instant now) {
        id = new Id(accountId, kind);
        status = VerificationStatus.PENDING;
        createdAt = now;
    }

    @Embeddable
    record Id(
            @Column(name = "specialist_account_id") UUID accountId,
            @Enumerated(EnumType.STRING) @Column(name = "scope_type") SpecialistKind kind)
            implements java.io.Serializable {
    }
}
