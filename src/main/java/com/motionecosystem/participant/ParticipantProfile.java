package com.motionecosystem.participant;

import java.time.Instant;
import java.time.ZoneId;
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
    @Column(name = "time_zone_id")
    String timeZoneId;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
    @Version
    long version;

    protected ParticipantProfile() {
    }

    ParticipantProfile(UUID accountId, String displayName, ZoneId timeZone, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.displayName = displayName;
        this.timeZoneId = timeZone == null ? null : timeZone.getId();
        this.createdAt = now;
        this.updatedAt = now;
    }

    ParticipantProfile(UUID accountId, String displayName, Instant now) {
        this(accountId, displayName, null, now);
    }

    void update(String name, Instant now) {
        update(name, null, now);
    }

    void update(String name, ZoneId timeZone, Instant now) {
        displayName = name;
        timeZoneId = timeZone == null ? null : timeZone.getId();
        updatedAt = now;
    }
}
