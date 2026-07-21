package com.motionecosystem.safety;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RestrictionV2Repository extends JpaRepository<RestrictionEntity, UUID> {
    List<RestrictionEntity> findByParticipantIdOrderByCreatedAt(UUID participantId);

    List<RestrictionEntity> findByParticipantIdAndStatus(
            UUID participantId, RestrictionEntity.Status status);
}

interface SafetyAssessmentRepository extends JpaRepository<SafetyAssessmentEntity, UUID> {
    Optional<SafetyAssessmentEntity> findFirstByRevisionIdOrderByAssessedAtDesc(UUID revisionId);
}

interface SafetyFactorRepository extends JpaRepository<SafetyFactorEntity, UUID> {
}

interface SafetyOverrideRepository extends JpaRepository<SafetyOverrideEntity, UUID> {
    List<SafetyOverrideEntity> findByAssessmentId(UUID assessmentId);
}
