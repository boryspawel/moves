package com.motionecosystem.specialist;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantSpecialistRelationshipRepository extends JpaRepository<ParticipantSpecialistRelationship, UUID> {

    boolean existsBySpecialistAccountIdAndParticipantAccountIdAndStatus(
            UUID specialistAccountId,
            UUID participantAccountId,
            ParticipantSpecialistRelationship.Status status);
}
