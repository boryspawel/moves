package com.motionecosystem.specialist;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantSpecialistRelationshipRepository extends JpaRepository<ParticipantSpecialistRelationship, UUID> {

    boolean existsBySpecialistAccountIdAndParticipantAccountIdAndStatus(
            UUID specialistAccountId,
            UUID participantAccountId,
            ParticipantSpecialistRelationship.Status status);

    boolean existsBySpecialistAccountIdAndParticipantIdAndStatus(
            UUID specialistAccountId, UUID participantId,
            ParticipantSpecialistRelationship.Status status);

    List<ParticipantSpecialistRelationship> findBySpecialistAccountIdAndStatus(
            UUID specialistAccountId, ParticipantSpecialistRelationship.Status status);

    Optional<ParticipantSpecialistRelationship> findBySpecialistAccountIdAndParticipantAccountId(
            UUID specialistAccountId, UUID participantAccountId);

    Optional<ParticipantSpecialistRelationship> findBySpecialistAccountIdAndParticipantId(
            UUID specialistAccountId, UUID participantId);

    List<ParticipantSpecialistRelationship> findBySpecialistAccountIdAndParticipantIdAndStatus(
            UUID specialistAccountId, UUID participantId, ParticipantSpecialistRelationship.Status status);
}
