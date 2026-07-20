package com.motionecosystem.specialist;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "specialist_profile", schema = "specialist")
class SpecialistProfile {

    @Id
    UUID id;
    @Column(name = "account_id", nullable = false, unique = true)
    UUID accountId;
    @Column(name = "display_name", nullable = false)
    String displayName;
    @Enumerated(EnumType.STRING)
    @Column(name = "specialist_kind", nullable = false)
    SpecialistKind specialistKind;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
    @Version
    long version;

    protected SpecialistProfile() {
    }

    SpecialistProfile(UUID accountId, String displayName, SpecialistKind kind, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.displayName = displayName;
        this.specialistKind = kind;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String name, SpecialistKind kind, Instant now) {
        displayName = name;
        specialistKind = kind;
        updatedAt = now;
    }
}
