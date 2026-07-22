package com.motionecosystem.specialist;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantSpecialistRelationshipRepository extends JpaRepository<ParticipantSpecialistRelationship, UUID> {

    boolean existsBySpecialistAccountIdAndParticipantAccountIdAndStatus(
            UUID specialistAccountId,
            UUID participantAccountId,
            ParticipantSpecialistRelationship.Status status);

    List<ParticipantSpecialistRelationship> findBySpecialistAccountIdAndStatus(
            UUID specialistAccountId, ParticipantSpecialistRelationship.Status status);
}
