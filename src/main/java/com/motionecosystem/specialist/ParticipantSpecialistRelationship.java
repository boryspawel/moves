package com.motionecosystem.specialist;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "participant_specialist_relationship", schema = "specialist")
class ParticipantSpecialistRelationship {

    enum Status { ACTIVE, ENDED }

    @Id
    private UUID id;
    @Column(name = "specialist_account_id", nullable = false)
    private UUID specialistAccountId;
    @Column(name = "participant_account_id", nullable = false)
    private UUID participantAccountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;
    @Column(name = "ended_at")
    private Instant endedAt;

    protected ParticipantSpecialistRelationship() {
    }

    UUID participantAccountId() {
        return participantAccountId;
    }

    Instant activatedAt() {
        return activatedAt;
    }

    Status status() {
        return status;
    }
}
