package com.motionecosystem.exerciseimport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExerciseImportSourceRepository extends JpaRepository<ExerciseImportSourceEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from ExerciseImportSourceJpaEntity source where source.id = :id")
    Optional<ExerciseImportSourceEntity> findLockedById(@Param("id") UUID id);
}
interface ExerciseImportBatchRepository extends JpaRepository<ExerciseImportBatchEntity, UUID> {}
interface ExerciseImportRecordRepository extends JpaRepository<ExerciseImportRecordEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from ExerciseImportRecordJpaEntity record where record.id = :id")
    Optional<ExerciseImportRecordEntity> findLockedById(@Param("id") UUID id);
    List<ExerciseImportRecordEntity> findByBatchIdAndStatusOrderByRowNumberAscIdAsc(UUID batchId, String status);
}
interface ExerciseImportSourceReferenceRepository extends JpaRepository<ExerciseImportSourceReferenceEntity, UUID> {
    Optional<ExerciseImportSourceReferenceEntity> findBySourceIdAndSourceRecordKey(UUID sourceId, String sourceRecordKey);
    List<ExerciseImportSourceReferenceEntity> findByNormalizedSha256AndSourceIdNotOrderByExerciseId(String normalizedSha256, UUID sourceId);
}
interface ExerciseImportMappingRepository extends JpaRepository<ExerciseImportMappingEntity, UUID> {
    Optional<ExerciseImportMappingEntity> findBySourceIdAndDictionaryTypeAndSourceValue(UUID sourceId, String dictionaryType, String sourceValue);
}
interface ExerciseImportIssueRepository extends JpaRepository<ExerciseImportIssueEntity, UUID> {
    boolean existsByRecordIdAndCodeAndResolvedAtIsNull(UUID recordId, String code);
    boolean existsByBatchIdAndRecordIdAndCodeAndJsonPointer(UUID batchId, UUID recordId, String code, String jsonPointer);
    List<ExerciseImportIssueEntity> findByRecordIdAndCodeAndResolvedAtIsNull(UUID recordId, String code);
}
interface ExerciseImportMatchCandidateRepository extends JpaRepository<ExerciseImportMatchCandidateEntity, UUID> {
    void deleteByRecordId(UUID recordId);
}
