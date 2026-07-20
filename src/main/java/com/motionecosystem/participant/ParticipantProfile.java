package com.motionecosystem.participant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "participant_profile", schema = "participant")
class ParticipantProfile {

    @Id
    UUID id;
    @Column(name = "account_id", nullable = false, unique = true)
    UUID accountId;
    @Column(name = "display_name", nullable = false)
    String displayName;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
    @Version
    long version;

    protected ParticipantProfile() {
    }

    ParticipantProfile(UUID accountId, String displayName, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.displayName = displayName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String name, Instant now) {
        displayName = name;
        updatedAt = now;
    }
}
