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
}
interface ExerciseSetAnalysisRunRepository extends JpaRepository<ExerciseSetEntities.ExerciseSetAnalysisRunEntity, UUID> {
    @EntityGraph(attributePaths="findings") Optional<ExerciseSetEntities.ExerciseSetAnalysisRunEntity> findByVersionId(UUID versionId);
}
interface ExerciseSetAnatomyAnalysisRunRepository extends JpaRepository<ExerciseSetEntities.ExerciseSetAnatomyAnalysisRunEntity, UUID> {
    Optional<ExerciseSetEntities.ExerciseSetAnatomyAnalysisRunEntity> findByVersionId(UUID versionId);
}
