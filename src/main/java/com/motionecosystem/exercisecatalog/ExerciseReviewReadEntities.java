package com.motionecosystem.exercisecatalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity(name = "ImportRecordReviewJpaEntity")
@Immutable
@Table(name = "import_record", schema = "exercise_import")
class ImportRecord {
    @Id UUID id;
    @Column(name = "batch_id", nullable = false) UUID batchId;
    @Column(name = "row_number", nullable = false) long rowNumber;
    @Column(name = "source_record_key") String sourceRecordKey;
    @Column(nullable = false) String status;
    @Column(name = "draft_version_id") UUID draftVersionId;
    @Column(name = "normalized_payload", columnDefinition = "jsonb") String normalizedPayload;
}

@Entity(name = "ImportIssueReviewJpaEntity")
@Immutable
@Table(name = "import_issue", schema = "exercise_import")
class ImportIssue {
    @Id UUID id;
    @Column(name = "record_id") UUID recordId;
    @Column(nullable = false) String severity;
    @Column(nullable = false) String code;
    @Column(name = "json_pointer", nullable = false) String jsonPointer;
    @Column(nullable = false) String message;
    @Column(name = "resolved_at") Instant resolvedAt;
}

@Entity(name = "ExerciseReviewReadJpaEntity")
@Table(name = "exercise_review", schema = "exercise_catalog")
class ExerciseReview {
    @Id UUID id;
    @Column(name = "exercise_version_id", nullable = false) UUID exerciseVersionId;
    @Column(name = "review_area", nullable = false) String reviewArea;
    @Column(nullable = false) String decision;
    @Column(length = 4000) String comment;
    @Column(name = "reviewer_subject", nullable = false) String reviewerSubject;
    @Column(name = "reviewed_at", nullable = false) Instant reviewedAt;
    @jakarta.persistence.Version long version;
    @Column(name = "content_revision", nullable = false) long contentRevision;
    @Column(name = "invalidated_at") Instant invalidatedAt;
    @Column(name = "invalidated_by_subject") String invalidatedBySubject;

    protected ExerciseReview() {
    }

    ExerciseReview(UUID versionId, String area, String decision, String comment, String reviewerSubject,
                   Instant reviewedAt, long contentRevision) {
        this.id = UUID.randomUUID();
        this.exerciseVersionId = versionId;
        this.reviewArea = area;
        this.decision = decision;
        this.comment = comment;
        this.reviewerSubject = reviewerSubject;
        this.reviewedAt = reviewedAt;
        this.contentRevision = contentRevision;
    }

    void invalidate(String actorSubject, Instant at) {
        invalidatedAt = at;
        invalidatedBySubject = actorSubject;
    }
}

@Entity(name = "ExerciseVersionPurposeJpaEntity")
@Immutable
@Table(name = "exercise_version_purpose", schema = "exercise_catalog")
class ExerciseVersionPurpose {
    @EmbeddedId ExerciseVersionPurposeId id;
}

@Embeddable
class ExerciseVersionPurposeId implements Serializable {
    @Column(name = "exercise_version_id", nullable = false) UUID exerciseVersionId;
    @Column(nullable = false) String purpose;

    protected ExerciseVersionPurposeId() {
    }
}

@Entity(name = "ExerciseMediaReviewJpaEntity")
@Immutable
@Table(name = "exercise_media", schema = "exercise_catalog")
class ExerciseMedia {
    @EmbeddedId ExerciseMediaId id;
}

@Embeddable
class ExerciseMediaId implements Serializable {
    @Column(name = "exercise_version_id", nullable = false) UUID exerciseVersionId;
    @Column(name = "media_asset_id", nullable = false) UUID mediaAssetId;
    @Column(nullable = false) String role;

    protected ExerciseMediaId() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExerciseMediaId value)) return false;
        return Objects.equals(exerciseVersionId, value.exerciseVersionId)
                && Objects.equals(mediaAssetId, value.mediaAssetId) && Objects.equals(role, value.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exerciseVersionId, mediaAssetId, role);
    }
}
