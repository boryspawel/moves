package com.motionecosystem.exercisesets.infrastructure;

import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExerciseSetRepository extends JpaRepository<ExerciseSetEntities.ExerciseSetEntity, UUID> {
    List<ExerciseSetEntities.ExerciseSetEntity> findByOwnerAccountIdOrderByCreatedAtDesc(UUID ownerAccountId);
}
interface ExerciseSetVersionRepository extends JpaRepository<ExerciseSetEntities.ExerciseSetVersionEntity, UUID> {
    @EntityGraph(attributePaths={"items","items.dose"}) Optional<ExerciseSetEntities.ExerciseSetVersionEntity> findWithItemsById(UUID id);
    @EntityGraph(attributePaths={"items","items.dose"}) Optional<ExerciseSetEntities.ExerciseSetVersionEntity> findFirstByExerciseSetIdAndStatusOrderByVersionNumberDesc(UUID setId, com.motionecosystem.exercisesets.domain.ExerciseSetModel.VersionStatus status);
    List<ExerciseSetEntities.ExerciseSetVersionEntity> findByExerciseSetIdOrderByVersionNumberDesc(UUID setId);
    long countByExerciseSetId(UUID setId);
    @EntityGraph(attributePaths={"items","items.dose"})
    @org.springframework.data.jpa.repository.Query("select v from ExerciseSetVersion v join ExerciseSetVersionGrant g on g.versionId = v.id where g.participantId = :participantId and g.revokedAt is null and v.status = com.motionecosystem.exercisesets.domain.ExerciseSetModel$VersionStatus.PUBLISHED order by v.publishedAt desc")
    List<ExerciseSetEntities.ExerciseSetVersionEntity> findPublishedAvailableToParticipantId(UUID participantId);
}
interface ExerciseSetVersionGrantRepository extends JpaRepository<ExerciseSetEntities.ExerciseSetVersionGrantEntity, UUID> {
    Optional<ExerciseSetEntities.ExerciseSetVersionGrantEntity> findByVersionIdAndParticipantId(UUID versionId, UUID participantId);
    boolean existsByVersionIdAndParticipantIdAndRevokedAtIsNull(UUID versionId, UUID participantId);
    List<ExerciseSetEntities.ExerciseSetVersionGrantEntity> findByVersionIdOrderByGrantedAtDesc(UUID versionId);
}
interface ExerciseSetAnalysisRunRepository extends JpaRepository<ExerciseSetEntities.ExerciseSetAnalysisRunEntity, UUID> {
    @EntityGraph(attributePaths="findings") Optional<ExerciseSetEntities.ExerciseSetAnalysisRunEntity> findByVersionId(UUID versionId);
}
interface ExerciseSetAnatomyAnalysisRunRepository extends JpaRepository<ExerciseSetEntities.ExerciseSetAnatomyAnalysisRunEntity, UUID> {
    Optional<ExerciseSetEntities.ExerciseSetAnatomyAnalysisRunEntity> findByVersionId(UUID versionId);
}
