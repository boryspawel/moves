package com.motionecosystem.exercisecatalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportRecordReviewRepository extends JpaRepository<ImportRecord, UUID> {
    Optional<ImportRecord> findByDraftVersionId(UUID draftVersionId);
    List<ImportRecord> findByDraftVersionIdIn(Collection<UUID> draftVersionIds);
}

interface ImportIssueReviewRepository extends JpaRepository<ImportIssue, UUID> {
    List<ImportIssue> findByRecordIdOrderBySeverityAscCodeAscIdAsc(UUID recordId);
    List<ImportIssue> findByRecordIdIn(Collection<UUID> recordIds);
    long countByRecordIdAndResolvedAtIsNullAndSeverityIn(UUID recordId, Collection<String> severities);
}

interface ExerciseReviewReadRepository extends JpaRepository<ExerciseReview, UUID> {
    List<ExerciseReview> findByExerciseVersionIdInAndInvalidatedAtIsNull(Collection<UUID> exerciseVersionIds);
    List<ExerciseReview> findByExerciseVersionIdOrderByReviewedAtAscIdAsc(UUID exerciseVersionId);
    List<ExerciseReview> findByExerciseVersionIdAndInvalidatedAtIsNull(UUID exerciseVersionId);
}

interface ExerciseMediaReviewRepository extends JpaRepository<ExerciseMedia, ExerciseMediaId> {
    List<ExerciseMedia> findByIdExerciseVersionIdIn(Collection<UUID> exerciseVersionIds);
}

interface ExerciseVersionPurposeRepository extends JpaRepository<ExerciseVersionPurpose, ExerciseVersionPurposeId> {
    boolean existsByIdExerciseVersionIdAndIdPurpose(UUID exerciseVersionId, String purpose);
    List<ExerciseVersionPurpose> findByIdExerciseVersionIdInAndIdPurpose(Collection<UUID> exerciseVersionIds, String purpose);
}
