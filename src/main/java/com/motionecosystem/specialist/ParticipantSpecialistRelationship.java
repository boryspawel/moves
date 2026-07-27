package com.motionecosystem.specialist;

import com.motionecosystem.participant.ParticipantRecord;

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
    /** Deprecated compatibility bridge; account-free records leave this null. */
    @Column(name = "participant_account_id")
    private UUID participantAccountId;
    @Column(name = "participant_id")
    private UUID participantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_context")
    private ParticipantRecord.RelationshipContext relationshipContext;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;
    @Column(name = "ended_at")
    private Instant endedAt;

    protected ParticipantSpecialistRelationship() {
    }

    ParticipantSpecialistRelationship(UUID specialistAccountId, UUID participantId,
            ParticipantRecord.RelationshipContext relationshipContext, Instant activatedAt) {
        this.id = UUID.randomUUID();
        this.specialistAccountId = specialistAccountId;
        this.participantId = participantId;
        this.relationshipContext = relationshipContext;
        this.status = Status.ACTIVE;
        this.activatedAt = activatedAt;
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

    UUID participantId() { return participantId; }
}
