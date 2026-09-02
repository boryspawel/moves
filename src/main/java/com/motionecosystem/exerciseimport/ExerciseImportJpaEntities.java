package com.motionecosystem.exerciseimport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Internal persistence model for the exercise-import aggregate. */
@Entity(name = "ExerciseImportSourceJpaEntity")
@Table(name = "import_source", schema = "exercise_import")
class ExerciseImportSourceEntity {
    @Id UUID id;
    @Column(nullable = false) String code;
    @Column(name = "display_name", nullable = false) String displayName;
    @Column(name = "default_locale", nullable = false) String defaultLocale;
    @Column(name = "license_code", nullable = false) String licenseCode;
    @Column(name = "license_verified", nullable = false) boolean licenseVerified;
    @Column(nullable = false) boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "created_by_subject", nullable = false, updatable = false) String createdBySubject;
    @Version long version;
}

@Entity(name = "ExerciseImportBatchJpaEntity")
@Table(name = "import_batch", schema = "exercise_import")
class ExerciseImportBatchEntity {
    @Id UUID id;
    @Column(name = "source_id", nullable = false) UUID sourceId;
    @Column(name = "request_key", nullable = false) String requestKey;
    @Column(nullable = false) String status;
    @Column(name = "forced_from_batch_id") UUID forcedFromBatchId;
    @Column(name = "submitted_by_subject", nullable = false) String submittedBySubject;
    @Column(name = "submitted_at", nullable = false) Instant submittedAt;
    @Column(name = "started_at") Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "total_count", nullable = false) int totalCount;
    @Column(name = "valid_count", nullable = false) int validCount;
    @Column(name = "invalid_count", nullable = false) int invalidCount;
    @Column(name = "blocked_count", nullable = false) int blockedCount;
    @Column(name = "drafted_count", nullable = false) int draftedCount;
    @Column(name = "unchanged_count", nullable = false) int unchangedCount;
    @Version long version;
}

@Entity(name = "ExerciseImportRecordJpaEntity")
@Table(name = "import_record", schema = "exercise_import")
class ExerciseImportRecordEntity {
    @Id UUID id;
    @Column(name = "batch_id", nullable = false) UUID batchId;
    @Column(name = "row_number", nullable = false) long rowNumber;
    @Column(name = "source_record_key") String sourceRecordKey;
    @Column(nullable = false) String status;
    /** Raw input is immutable in the database; V017's trigger remains the authority. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw_payload", nullable = false, updatable = false, columnDefinition = "jsonb") String rawPayload;
    @Column(name = "raw_sha256", nullable = false, updatable = false) String rawSha256;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "normalized_payload", columnDefinition = "jsonb") String normalizedPayload;
    @Column(name = "normalized_sha256") String normalizedSha256;
    @Column(name = "normalization_version") String normalizationVersion;
    @Column(name = "matched_exercise_id") UUID matchedExerciseId;
    @Column(name = "draft_version_id") UUID draftVersionId;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "processing_token") UUID processingToken;
    @Version long version;
}

@Entity(name = "ExerciseImportSourceReferenceJpaEntity")
@Table(name = "import_source_reference", schema = "exercise_import")
class ExerciseImportSourceReferenceEntity {
    @Id UUID id;
    @Column(name = "source_id", nullable = false) UUID sourceId;
    @Column(name = "source_record_key", nullable = false) String sourceRecordKey;
    @Column(name = "exercise_id", nullable = false) UUID exerciseId;
    @Column(name = "latest_exercise_version_id", nullable = false) UUID latestExerciseVersionId;
    @Column(name = "normalized_sha256", nullable = false) String normalizedSha256;
    @Column(name = "first_record_id", nullable = false) UUID firstRecordId;
    @Column(name = "last_record_id", nullable = false) UUID lastRecordId;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Version long version;
}

@Entity(name = "ExerciseImportMappingJpaEntity")
@Table(name = "import_mapping", schema = "exercise_import")
class ExerciseImportMappingEntity {
    @Id UUID id;
    @Column(name = "source_id", nullable = false) UUID sourceId;
    @Column(name = "dictionary_type", nullable = false) String dictionaryType;
    @Column(name = "source_value", nullable = false) String sourceValue;
    @Column(name = "canonical_value") String canonicalValue;
    @Column(nullable = false) String status;
    @Column(name = "decided_by_subject") String decidedBySubject;
    @Column(name = "decided_at") Instant decidedAt;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Version long version;
}

@Entity(name = "ExerciseImportIssueJpaEntity")
@Table(name = "import_issue", schema = "exercise_import")
class ExerciseImportIssueEntity {
    @Id UUID id;
    @Column(name = "batch_id", nullable = false) UUID batchId;
    @Column(name = "record_id") UUID recordId;
    @Column(name = "row_number") Long rowNumber;
    @Column(nullable = false) String code;
    @Column(nullable = false) String stage;
    @Column(nullable = false) String severity;
    @Column(name = "json_pointer", nullable = false) String jsonPointer;
    @Column(nullable = false) String message;
    @Column(name = "resolved_at") Instant resolvedAt;
    @Column(name = "resolved_by_subject") String resolvedBySubject;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
}

@Entity(name = "ExerciseImportMatchCandidateJpaEntity")
@Table(name = "import_match_candidate", schema = "exercise_import")
class ExerciseImportMatchCandidateEntity {
    @Id UUID id;
    @Column(name = "record_id", nullable = false) UUID recordId;
    @Column(name = "exercise_id", nullable = false) UUID exerciseId;
    @Column(nullable = false) int rank;
    @Column(nullable = false, precision = 8, scale = 5) BigDecimal score;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") String reasons;
    @Column(name = "algorithm_version", nullable = false) String algorithmVersion;
    String decision;
    @Column(name = "decided_by_subject") String decidedBySubject;
    @Column(name = "decided_at") Instant decidedAt;
    @Version long version;
}
