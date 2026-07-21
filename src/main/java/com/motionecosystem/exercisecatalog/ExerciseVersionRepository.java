package com.motionecosystem.exercisecatalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExerciseVersionRepository extends JpaRepository<ExerciseVersion, UUID> {
    List<ExerciseVersion> findByExerciseIdOrderByVersionNumber(UUID exerciseId);
    Optional<ExerciseVersion> findFirstByExerciseIdOrderByVersionNumberDesc(UUID exerciseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT version FROM ExerciseVersion version WHERE version.id = :id")
    Optional<ExerciseVersion> findLockedById(@Param("id") UUID id);

    @Query(value = """
            SELECT exercise.id AS exerciseId, exercise.canonicalName AS canonicalName,
                   version.id AS versionId, version.versionNumber AS versionNumber,
                   version.movementPattern AS primaryMovementPattern,
                   version.technicalLevel AS technicalLevel, version.environment AS environment
            FROM ExerciseVersion version
            JOIN Exercise exercise ON exercise.id = version.exerciseId
            WHERE version.status = com.motionecosystem.exercisecatalog.ExerciseVersionStatus.PUBLISHED
              AND NOT EXISTS (
                  SELECT newer.id FROM ExerciseVersion newer
                  WHERE newer.exerciseId = version.exerciseId
                    AND newer.status = com.motionecosystem.exercisecatalog.ExerciseVersionStatus.PUBLISHED
                    AND newer.versionNumber > version.versionNumber
              )
              AND (:queryDisabled = TRUE OR LOWER(exercise.canonicalName) LIKE :queryPattern
                   OR LOWER(version.instruction) LIKE :queryPattern)
              AND (:movementDisabled = TRUE OR :movementPattern MEMBER OF version.movementPatterns)
              AND (:technicalDisabled = TRUE OR version.technicalLevel = :technicalLevel)
              AND (:equipmentDisabled = TRUE OR :equipment MEMBER OF version.requiredEquipment)
            ORDER BY LOWER(exercise.canonicalName), exercise.id
            """, countQuery = """
            SELECT COUNT(version.id)
            FROM ExerciseVersion version
            JOIN Exercise exercise ON exercise.id = version.exerciseId
            WHERE version.status = com.motionecosystem.exercisecatalog.ExerciseVersionStatus.PUBLISHED
              AND NOT EXISTS (
                  SELECT newer.id FROM ExerciseVersion newer
                  WHERE newer.exerciseId = version.exerciseId
                    AND newer.status = com.motionecosystem.exercisecatalog.ExerciseVersionStatus.PUBLISHED
                    AND newer.versionNumber > version.versionNumber
              )
              AND (:queryDisabled = TRUE OR LOWER(exercise.canonicalName) LIKE :queryPattern
                   OR LOWER(version.instruction) LIKE :queryPattern)
              AND (:movementDisabled = TRUE OR :movementPattern MEMBER OF version.movementPatterns)
              AND (:technicalDisabled = TRUE OR version.technicalLevel = :technicalLevel)
              AND (:equipmentDisabled = TRUE OR :equipment MEMBER OF version.requiredEquipment)
            """)
    Page<CatalogListProjection> findCurrentPublished(
            @Param("queryDisabled") boolean queryDisabled,
            @Param("queryPattern") String queryPattern,
            @Param("movementDisabled") boolean movementDisabled,
            @Param("movementPattern") MovementPattern movementPattern,
            @Param("technicalDisabled") boolean technicalDisabled,
            @Param("technicalLevel") TechnicalLevel technicalLevel,
            @Param("equipmentDisabled") boolean equipmentDisabled,
            @Param("equipment") String equipment,
            Pageable pageable);

    List<ExerciseVersion> findByIdInAndStatus(Set<UUID> ids, ExerciseVersionStatus status);

    @Query("""
            SELECT version.id AS versionId, pattern AS movementPattern
            FROM ExerciseVersion version JOIN version.movementPatterns pattern
            WHERE version.id IN :ids
            """)
    List<MovementPatternProjection> findMovementPatterns(@Param("ids") Set<UUID> ids);

    @Query("""
            SELECT version.id AS versionId, equipment AS equipment
            FROM ExerciseVersion version JOIN version.requiredEquipment equipment
            WHERE version.id IN :ids
            """)
    List<EquipmentProjection> findEquipment(@Param("ids") Set<UUID> ids);

    @Query("""
            SELECT tag AS tag, COUNT(DISTINCT version.id) AS versionCount
            FROM ExerciseVersion version JOIN version.contraindicationTags tag
            GROUP BY tag ORDER BY tag
            """)
    List<LegacyTagProjection> legacyContraindicationReport();

    interface CatalogListProjection {
        UUID getExerciseId();
        String getCanonicalName();
        UUID getVersionId();
        int getVersionNumber();
        MovementPattern getPrimaryMovementPattern();
        TechnicalLevel getTechnicalLevel();
        ExerciseEnvironment getEnvironment();
    }

    interface MovementPatternProjection {
        UUID getVersionId();
        MovementPattern getMovementPattern();
    }

    interface EquipmentProjection {
        UUID getVersionId();
        String getEquipment();
    }

    interface LegacyTagProjection {
        String getTag();
        long getVersionCount();
    }
}
