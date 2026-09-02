package com.motionecosystem.exerciseimport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExerciseImportSourceRepository extends JpaRepository<ExerciseImportSourceEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from ExerciseImportSourceJpaEntity source where source.id = :id")
    Optional<ExerciseImportSourceEntity> findLockedById(@Param("id") UUID id);
    List<ExerciseImportSourceEntity> findAllByOrderByCodeAscIdAsc();
}
interface ExerciseImportBatchRepository extends JpaRepository<ExerciseImportBatchEntity, UUID> {
    Optional<ExerciseImportBatchEntity> findBySourceIdAndRequestKey(UUID sourceId, String requestKey);
    List<ExerciseImportBatchEntity> findBySourceId(UUID sourceId);
}
interface ExerciseImportArtifactRepository extends JpaRepository<ExerciseImportArtifactEntity, UUID> {
    Optional<ExerciseImportArtifactEntity> findByBatchId(UUID batchId);
    @Query("""
            select artifact from ExerciseImportArtifactJpaEntity artifact
            join ExerciseImportBatchJpaEntity batch on batch.id = artifact.batchId
            where batch.sourceId = :sourceId and artifact.sha256 = :sha256
            order by batch.submittedAt, batch.id
            """)
    List<ExerciseImportArtifactEntity> findBySourceIdAndSha256OrderBySubmittedAt(
            @Param("sourceId") UUID sourceId, @Param("sha256") String sha256, Pageable pageable);
}
interface ExerciseImportRecordRepository extends JpaRepository<ExerciseImportRecordEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from ExerciseImportRecordJpaEntity record where record.id = :id")
    Optional<ExerciseImportRecordEntity> findLockedById(@Param("id") UUID id);
    List<ExerciseImportRecordEntity> findByBatchIdAndStatusOrderByRowNumberAscIdAsc(UUID batchId, String status);
    boolean existsByBatchIdAndRowNumber(UUID batchId, long rowNumber);
    List<ExerciseImportRecordEntity> findByBatchIdAndStatusAndDraftVersionIdIsNullOrderByRowNumberAscIdAsc(UUID batchId, String status);
    List<ExerciseImportRecordEntity> findByBatchId(UUID batchId);
    @Query("""
            select record from ExerciseImportRecordJpaEntity record
            where record.batchId = :batchId
              and (:status is null or record.status = :status)
              and (:severity is null or exists (select issue.id from ExerciseImportIssueJpaEntity issue
                  where issue.recordId = record.id and issue.severity = :severity))
            order by record.rowNumber, record.id
            """)
    Page<ExerciseImportRecordEntity> findPage(@Param("batchId") UUID batchId, @Param("status") String status,
                                               @Param("severity") String severity, Pageable pageable);
    @Query("""
            select record from ExerciseImportRecordJpaEntity record
            join ExerciseImportBatchJpaEntity batch on batch.id = record.batchId
            where batch.sourceId = :sourceId and record.status = :status
              and lower(record.rawPayload) like lower(concat('%', :sourceValue, '%'))
            order by record.id
            """)
    List<ExerciseImportRecordEntity> findBlockedForMapping(@Param("sourceId") UUID sourceId,
                                                            @Param("status") String status,
                                                            @Param("sourceValue") String sourceValue);
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
    boolean existsByBatchIdAndRecordIdIsNullAndCodeAndJsonPointer(UUID batchId, String code, String jsonPointer);
    List<ExerciseImportIssueEntity> findByRecordIdAndCodeAndResolvedAtIsNull(UUID recordId, String code);
    List<ExerciseImportIssueEntity> findByRecordIdOrderBySeverityAscCodeAscIdAsc(UUID recordId);
    List<ExerciseImportIssueEntity> findByBatchIdOrderByRowNumberAscSeverityAscCodeAscIdAsc(UUID batchId);
    void deleteByRecordIdAndCode(UUID recordId, String code);
}
interface ExerciseImportMatchCandidateRepository extends JpaRepository<ExerciseImportMatchCandidateEntity, UUID> {
    void deleteByRecordId(UUID recordId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select candidate from ExerciseImportMatchCandidateJpaEntity candidate where candidate.id = :id and candidate.recordId = :recordId")
    Optional<ExerciseImportMatchCandidateEntity> findLockedByIdAndRecordId(@Param("id") UUID id, @Param("recordId") UUID recordId);
    List<ExerciseImportMatchCandidateEntity> findByRecordIdOrderByRankAscIdAsc(UUID recordId);
    boolean existsByRecordIdAndDecisionNotOrDecisionIsNull(UUID recordId, String decision);
}
