package com.motionecosystem.safety;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

interface RestrictionV2Repository extends JpaRepository<RestrictionEntity, UUID> {
    @Override
    @EntityGraph(attributePaths = "target")
    Optional<RestrictionEntity> findById(UUID id);

    @EntityGraph(attributePaths = "target")
    List<RestrictionEntity> findByParticipantIdOrderByCreatedAt(UUID participantId);

    @EntityGraph(attributePaths = "target")
    List<RestrictionEntity> findByParticipantIdAndStatus(
            UUID participantId, RestrictionEntity.Status status);
}

interface SafetyAssessmentRepository extends JpaRepository<SafetyAssessmentEntity, UUID> {
    @Override
    @EntityGraph(attributePaths = "factors")
    Optional<SafetyAssessmentEntity> findById(UUID id);

    @EntityGraph(attributePaths = "factors")
    Optional<SafetyAssessmentEntity> findFirstByRevisionIdOrderByAssessedAtDesc(UUID revisionId);
}

interface SafetyFactorRepository extends JpaRepository<SafetyFactorEntity, UUID> {
}

interface SafetyOverrideRepository extends JpaRepository<SafetyOverrideEntity, UUID> {
    List<SafetyOverrideEntity> findByAssessmentId(UUID assessmentId);
}
