package com.motionecosystem.safety;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "participant_restriction", schema = "safety")
class ParticipantRestriction {

    @Id
    UUID id;
    @Column(name = "account_id", nullable = false)
    UUID accountId;
    @Column(name = "contraindication_tag", nullable = false)
    String contraindicationTag;
    @Column(name = "recorded_at", nullable = false)
    Instant recordedAt;

    protected ParticipantRestriction() {
    }

    ParticipantRestriction(UUID accountId, String tag, Instant now) {
        id = UUID.randomUUID();
        this.accountId = accountId;
        contraindicationTag = tag;
        recordedAt = now;
    }
}
