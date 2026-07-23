package com.motionecosystem.exercisecatalog;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.audit.api.TransactionalOutbox;
import com.motionecosystem.exercisecatalog.api.PublishExerciseVersion;
import com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExerciseEditorialWorkflowService implements ReviewExerciseVersion, PublishExerciseVersion {
    private static final Set<String> AREAS = Set.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE", "MEDIA");
    private static final Set<String> DECISIONS = Set.of("APPROVED", "CHANGES_REQUESTED");
    private final ExerciseVersionRepository versions;
    private final ExerciseReviewReadRepository reviews;
    private final ExerciseMediaReviewRepository media;
    private final ExerciseLoadCharacteristicRepository characteristics;
    private final ExerciseContributionRepository contributions;
    private final EvidenceSourceRepository evidence;
    private final ImportRecordReviewRepository records;
    private final ImportIssueReviewRepository issues;
    private final TransactionalOutbox outbox;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public ReviewResult review(UUID versionId, String actor, ReviewCommand command) {
        if (command == null) throw badRequest("review is required");
        String area = normalized(command.area(), AREAS, "review area");
        String decision = normalized(command.decision(), DECISIONS, "review decision");
        ExerciseVersion version = locked(versionId);
        requireExpected(command.expectedVersion(), version.version);
        try {
            version.beginReview();
        } catch (IllegalStateException invalid) {
            throw conflict(invalid.getMessage());
        }
        Instant now = clock.instant();
        reviews.save(new ExerciseReview(versionId, area, decision, trim(command.comment(), 4000), actor, now,
                version.contentRevision));
        if ("CHANGES_REQUESTED".equals(decision)) {
            version.requestChanges();
        } else if (unmet(version).isEmpty()) {
            version.approve(actor, now);
        }
        versions.flush();
        audit.record(actor, "EXERCISE_VERSION_REVIEW_RECORDED", "ExerciseVersion", versionId);
        return reviewResult(version);
    }

    @Override
    @Transactional
    public PublicationResult publish(UUID versionId, String actor, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) throw badRequest("expected version is required");
        ExerciseVersion version = locked(versionId);
        requireExpected(expectedVersion, version.version);
        List<String> unmet = unmet(version);
        if (version.status != ExerciseVersionStatus.APPROVED) unmet = append(unmet, "STATUS_APPROVED_REQUIRED");
        if (!unmet.isEmpty()) throw conflict("publication requirements not met: " + String.join(",", unmet));
        Instant now = clock.instant();
        try {
            version.publish(now);
        } catch (IllegalStateException invalid) {
            throw conflict(invalid.getMessage());
        }
        versions.flush();
        outbox.append("ExerciseVersion", versionId, "ExerciseVersionPublished",
                "{\"exerciseVersionId\":\"" + versionId + "\",\"publishedBy\":\"" + json(actor) + "\"}", now);
        audit.record(actor, "EXERCISE_VERSION_PUBLISHED", "ExerciseVersion", versionId);
        audit.record(actor, "CAPABILITY_PUBLISH_EXERCISE_CONTENT", "ExerciseVersion", versionId);
        return new PublicationResult(versionId, version.status.name(), now, version.version, List.of());
    }

    @Transactional(readOnly = true)
    public ReviewResult status(UUID versionId) { return reviewResult(version(versionId)); }

    @Transactional(readOnly = true)
    public boolean readyToPublish(UUID versionId) {
        ExerciseVersion version = version(versionId);
        return version.status == ExerciseVersionStatus.APPROVED && unmet(version).isEmpty();
    }

    @Transactional(readOnly = true)
    public VersionDiff diff(UUID versionId) {
        ExerciseVersion value = version(versionId);
        String published = versions.findPublishedSemanticSha256(value.exerciseId, PageRequest.of(0, 1)).stream()
                .findFirst().orElse(null);
        return new VersionDiff(value.id, value.exerciseId, value.versionNumber, value.status.name(), value.semanticSha256, published);
    }

    private List<String> unmet(ExerciseVersion version) {
        List<String> result = new ArrayList<>();
        for (String area : List.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE")) {
            if (!latestApproved(version, area)) result.add("REVIEW_" + area + "_REQUIRED");
        }
        if (hasMedia(version.id) && !latestApproved(version, "MEDIA")) result.add("REVIEW_MEDIA_REQUIRED");
        if (!characteristics.existsByExerciseVersionId(version.id)) result.add("LOAD_CHARACTERISTIC_REQUIRED");
        if (!contributions.existsByExerciseVersionId(version.id)) result.add("ANATOMY_EXPOSURE_REQUIRED");
        if (!evidence.existsByExerciseVersionId(version.id)) result.add("EVIDENCE_REQUIRED");
        records.findByDraftVersionId(version.id).ifPresent(record -> {
            if (issues.countByRecordIdAndResolvedAtIsNullAndSeverityIn(record.id, List.of("ERROR", "BLOCKER")) > 0) {
                result.add("UNRESOLVED_IMPORT_ISSUES");
            }
        });
        return List.copyOf(result);
    }

    private ReviewResult reviewResult(ExerciseVersion version) {
        return new ReviewResult(version.id, version.status.name(), version.version, reviews(version.id), unmet(version), requiredAreas(version.id));
    }

    private List<String> requiredAreas(UUID versionId) {
        List<String> result = new ArrayList<>(List.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE"));
        if (hasMedia(versionId)) result.add("MEDIA");
        return List.copyOf(result);
    }

    private boolean latestApproved(ExerciseVersion version, String area) {
        return latestReview(version, area)
                .map(item -> "APPROVED".equals(item.decision)).orElse(false);
    }

    private java.util.Optional<ExerciseReview> latestReview(ExerciseVersion version, String area) {
        return reviews.findByExerciseVersionIdAndInvalidatedAtIsNull(version.id).stream()
                .filter(item -> item.contentRevision == version.contentRevision && area.equals(item.reviewArea))
                .max(Comparator.comparing((ExerciseReview item) -> item.reviewedAt).thenComparing(item -> item.id));
    }

    private boolean hasMedia(UUID versionId) { return !media.findByIdExerciseVersionIdIn(List.of(versionId)).isEmpty(); }
    private List<ReviewItem> reviews(UUID id) { return reviews.findByExerciseVersionIdOrderByReviewedAtAscIdAsc(id).stream()
            .map(item -> new ReviewItem(item.id, item.reviewArea, item.decision, item.comment, item.reviewerSubject,
                    item.reviewedAt, item.version, item.contentRevision, item.invalidatedAt, item.invalidatedBySubject)).toList(); }
    private ExerciseVersion locked(UUID id) { return versions.findLockedById(id).orElseThrow(this::notFound); }
    private ExerciseVersion version(UUID id) { return versions.findById(id).orElseThrow(this::notFound); }
    private static void requireExpected(Long expected, long actual) { if (expected != null && expected != actual) throw conflict("exercise version was changed concurrently"); }
    private static String normalized(String value, Set<String> allowed, String field) { String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT); if (!allowed.contains(result)) throw badRequest("invalid " + field); return result; }
    private static String trim(String value, int max) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw badRequest("comment is too long"); return result; }
    private static List<String> append(List<String> values, String value) { List<String> result = new ArrayList<>(values); result.add(value); return List.copyOf(result); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "exercise version not found"); }
    public record VersionDiff(UUID versionId, UUID exerciseId, int versionNumber, String status, String draftSemanticSha256,
                              String currentPublishedSemanticSha256) {}
}
