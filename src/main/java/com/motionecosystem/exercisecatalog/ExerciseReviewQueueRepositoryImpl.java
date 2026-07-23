package com.motionecosystem.exercisecatalog;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class ExerciseReviewQueueRepositoryImpl implements ExerciseReviewQueueRepositoryCustom {
    private static final List<String> REQUIRED_AREAS = List.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE");

    private final EntityManager entityManager;
    private final ImportRecordReviewRepository records;
    private final ImportIssueReviewRepository issues;
    private final ExerciseReviewReadRepository reviews;
    private final ExerciseMediaReviewRepository media;
    private final ExerciseLoadCharacteristicRepository loadCharacteristics;
    private final ExerciseContributionRepository contributions;
    private final EvidenceSourceRepository evidenceSources;

    ExerciseReviewQueueRepositoryImpl(EntityManager entityManager, ImportRecordReviewRepository records,
                                      ImportIssueReviewRepository issues, ExerciseReviewReadRepository reviews,
                                      ExerciseMediaReviewRepository media, ExerciseLoadCharacteristicRepository loadCharacteristics,
                                      ExerciseContributionRepository contributions, EvidenceSourceRepository evidenceSources) {
        this.entityManager = entityManager;
        this.records = records;
        this.issues = issues;
        this.reviews = reviews;
        this.media = media;
        this.loadCharacteristics = loadCharacteristics;
        this.contributions = contributions;
        this.evidenceSources = evidenceSources;
    }

    @Override
    public ExerciseReviewQueryService.QueuePage findQueue(ExerciseReviewQueueFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<ExerciseVersion> version = query.from(ExerciseVersion.class);
        Root<Exercise> exercise = query.from(Exercise.class);
        List<Predicate> predicates = predicates(cb, query, version, exercise, filter);
        query.multiselect(version.alias("version"), exercise.alias("exercise"))
                .where(predicates.toArray(Predicate[]::new));
        query.orderBy(order(cb, query, version, exercise, filter.sort()));
        List<Tuple> rows = entityManager.createQuery(query)
                .setFirstResult(filter.page() * filter.size()).setMaxResults(filter.size()).getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<ExerciseVersion> countVersion = countQuery.from(ExerciseVersion.class);
        Root<Exercise> countExercise = countQuery.from(Exercise.class);
        countQuery.select(cb.countDistinct(countVersion)).where(predicates(cb, countQuery, countVersion, countExercise, filter).toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        List<ExerciseVersion> versions = rows.stream().map(row -> row.get("version", ExerciseVersion.class)).toList();
        Map<UUID, Exercise> exercises = rows.stream().map(row -> row.get("exercise", Exercise.class))
                .collect(Collectors.toMap(value -> value.id, Function.identity()));
        return new ExerciseReviewQueryService.QueuePage(items(versions, exercises), filter.page(), filter.size(), total);
    }

    private List<ExerciseReviewQueryService.ExerciseReviewQueueItem> items(List<ExerciseVersion> versions, Map<UUID, Exercise> exercises) {
        if (versions.isEmpty()) return List.of();
        Set<UUID> versionIds = versions.stream().map(value -> value.id).collect(Collectors.toSet());
        Map<UUID, ImportRecord> recordsByVersion = records.findByDraftVersionIdIn(versionIds).stream()
                .collect(Collectors.toMap(value -> value.draftVersionId, Function.identity()));
        Map<UUID, List<ImportIssue>> issuesByRecord = recordsByVersion.isEmpty() ? Map.of()
                : issues.findByRecordIdIn(recordsByVersion.values().stream().map(value -> value.id).toList()).stream()
                        .collect(Collectors.groupingBy(value -> value.recordId));
        Map<UUID, List<ExerciseReview>> reviewsByVersion = reviews.findByExerciseVersionIdInAndInvalidatedAtIsNull(versionIds).stream()
                .collect(Collectors.groupingBy(value -> value.exerciseVersionId));
        Set<UUID> versionsWithMedia = media.findByIdExerciseVersionIdIn(versionIds).stream()
                .map(value -> value.id.exerciseVersionId).collect(Collectors.toSet());
        Set<UUID> versionsWithLoadCharacteristics = loadCharacteristics.findByExerciseVersionIdIn(versionIds).stream()
                .map(value -> value.exerciseVersionId).collect(Collectors.toSet());
        Set<UUID> versionsWithContributions = contributions.findByExerciseVersionIdIn(versionIds).stream()
                .map(value -> value.exerciseVersionId).collect(Collectors.toSet());
        Set<UUID> versionsWithEvidenceSources = evidenceSources.findByExerciseVersionIdIn(versionIds).stream()
                .map(value -> value.exerciseVersionId).collect(Collectors.toSet());
        return versions.stream().map(version -> item(version, exercises.get(version.exerciseId), recordsByVersion.get(version.id),
                issuesByRecord.getOrDefault(recordsByVersion.containsKey(version.id) ? recordsByVersion.get(version.id).id : null, List.of()),
                reviewsByVersion.getOrDefault(version.id, List.of()), versionsWithMedia.contains(version.id),
                versionsWithLoadCharacteristics.contains(version.id), versionsWithContributions.contains(version.id),
                versionsWithEvidenceSources.contains(version.id))).toList();
    }

    private ExerciseReviewQueryService.ExerciseReviewQueueItem item(ExerciseVersion version, Exercise exercise, ImportRecord record,
                                                                      List<ImportIssue> recordIssues, List<ExerciseReview> versionReviews,
                                                                      boolean hasMedia, boolean hasLoadCharacteristics,
                                                                      boolean hasContributions, boolean hasEvidenceSources) {
        int errors = (int) recordIssues.stream().filter(value -> value.resolvedAt == null && value.severity.equals("ERROR")).count();
        int warnings = (int) recordIssues.stream().filter(value -> value.resolvedAt == null && value.severity.equals("WARNING")).count();
        int blockers = (int) recordIssues.stream().filter(value -> value.resolvedAt == null && value.severity.equals("BLOCKER")).count();
        Map<String, ExerciseReview> latest = latest(version, versionReviews);
        List<String> required = new ArrayList<>(REQUIRED_AREAS);
        if (hasMedia) required.add("MEDIA");
        List<String> completed = required.stream().filter(area -> approved(latest.get(area))).sorted().toList();
        List<String> missing = required.stream().filter(area -> !approved(latest.get(area))).sorted().toList();
        boolean profilesComplete = hasLoadCharacteristics && hasContributions && hasEvidenceSources;
        boolean ready = version.status == ExerciseVersionStatus.APPROVED && errors == 0 && blockers == 0 && missing.isEmpty()
                && profilesComplete;
        boolean action = !ready && version.status != ExerciseVersionStatus.PUBLISHED && version.status != ExerciseVersionStatus.WITHDRAWN;
        return new ExerciseReviewQueryService.ExerciseReviewQueueItem(version.exerciseId, version.id, version.versionNumber,
                exercise.canonicalName, record == null ? null : record.sourceRecordKey, record == null ? null : record.batchId,
                record == null ? null : record.rowNumber, version.status.name(), record == null ? null : record.status, errors, warnings,
                blockers, completed, missing, ready, action, version.createdAt, version.version);
    }

    private Map<String, ExerciseReview> latest(ExerciseVersion version, List<ExerciseReview> source) {
        return source.stream().filter(value -> value.contentRevision == version.contentRevision)
                .collect(Collectors.toMap(value -> value.reviewArea, Function.identity(), (left, right) -> newer(left, right) ? left : right));
    }

    private boolean newer(ExerciseReview left, ExerciseReview right) {
        int byDate = left.reviewedAt.compareTo(right.reviewedAt);
        return byDate > 0 || byDate == 0 && left.id.compareTo(right.id) > 0;
    }

    private boolean approved(ExerciseReview review) {
        return review != null && review.decision.equals("APPROVED");
    }

    private List<Predicate> predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version,
                                       Root<Exercise> exercise, ExerciseReviewQueueFilter filter) {
        List<Predicate> result = new ArrayList<>();
        result.add(cb.equal(exercise.get("id"), version.get("exerciseId")));
        if (filter.batchId() != null) result.add(hasRecord(cb, query, version, filter.batchId()));
        if (filter.namePattern() != null) result.add(cb.like(cb.lower(exercise.get("canonicalName")), filter.namePattern()));
        if (filter.status() != null) result.add(cb.equal(version.get("status"), filter.status()));
        Expression<Boolean> ready = ready(cb, query, version);
        if (filter.readyToPublish() != null) result.add(cb.equal(ready, filter.readyToPublish()));
        if (filter.actionNeeded() != null) result.add(cb.equal(actionNeeded(cb, ready, version), filter.actionNeeded()));
        if (filter.hasErrors() != null) result.add(cb.equal(hasIssue(cb, query, version, "ERROR"), filter.hasErrors()));
        if (filter.hasBlockers() != null) result.add(cb.equal(hasIssue(cb, query, version, "BLOCKER"), filter.hasBlockers()));
        if (filter.missingReviewArea() != null) result.add(cb.not(latestApproved(cb, query, version, filter.missingReviewArea())));
        return result;
    }

    private Predicate hasRecord(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version, UUID batchId) {
        Subquery<UUID> recordQuery = query.subquery(UUID.class);
        Root<ImportRecord> record = recordQuery.from(ImportRecord.class);
        recordQuery.select(record.get("id")).where(cb.equal(record.get("draftVersionId"), version.get("id")),
                cb.equal(record.get("batchId"), batchId));
        return cb.exists(recordQuery);
    }

    private Expression<Boolean> ready(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(version.get("status"), ExerciseVersionStatus.APPROVED));
        conditions.add(cb.not(hasIssue(cb, query, version, "ERROR")));
        conditions.add(cb.not(hasIssue(cb, query, version, "BLOCKER")));
        for (String area : REQUIRED_AREAS) conditions.add(latestApproved(cb, query, version, area));
        conditions.add(cb.or(cb.not(hasMedia(cb, query, version)), latestApproved(cb, query, version, "MEDIA")));
        conditions.add(hasProfile(cb, query, version, ExerciseLoadCharacteristic.class));
        conditions.add(hasProfile(cb, query, version, ExerciseContribution.class));
        conditions.add(hasProfile(cb, query, version, EvidenceSource.class));
        return cb.and(conditions.toArray(Predicate[]::new));
    }

    private Expression<Boolean> actionNeeded(CriteriaBuilder cb, Expression<Boolean> ready, Root<ExerciseVersion> version) {
        return cb.and(cb.not(ready), cb.not(version.get("status").in(ExerciseVersionStatus.PUBLISHED, ExerciseVersionStatus.WITHDRAWN)));
    }

    private Predicate hasIssue(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version, String severity) {
        Subquery<UUID> issueQuery = query.subquery(UUID.class);
        Root<ImportIssue> issue = issueQuery.from(ImportIssue.class);
        Subquery<UUID> recordQuery = issueQuery.subquery(UUID.class);
        Root<ImportRecord> record = recordQuery.from(ImportRecord.class);
        recordQuery.select(record.get("id")).where(cb.equal(record.get("draftVersionId"), version.get("id")));
        issueQuery.select(issue.get("id")).where(cb.equal(issue.get("recordId"), recordQuery), cb.isNull(issue.get("resolvedAt")), cb.equal(issue.get("severity"), severity));
        return cb.exists(issueQuery);
    }

    private Predicate hasMedia(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version) {
        Subquery<ExerciseMedia> mediaQuery = query.subquery(ExerciseMedia.class);
        Root<ExerciseMedia> mediaRoot = mediaQuery.from(ExerciseMedia.class);
        mediaQuery.select(mediaRoot).where(cb.equal(mediaRoot.get("id").get("exerciseVersionId"), version.get("id")));
        return cb.exists(mediaQuery);
    }

    private Predicate hasProfile(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version, Class<?> type) {
        Subquery<UUID> profileQuery = query.subquery(UUID.class);
        Root<?> profile = profileQuery.from(type);
        profileQuery.select(profile.get("id").as(UUID.class)).where(cb.equal(profile.get("exerciseVersionId"), version.get("id")));
        return cb.exists(profileQuery);
    }

    private Predicate latestApproved(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version, String area) {
        Subquery<UUID> approved = query.subquery(UUID.class);
        Root<ExerciseReview> review = approved.from(ExerciseReview.class);
        Subquery<UUID> newer = approved.subquery(UUID.class);
        Root<ExerciseReview> later = newer.from(ExerciseReview.class);
        newer.select(later.get("id")).where(cb.equal(later.get("exerciseVersionId"), review.get("exerciseVersionId")),
                cb.equal(later.get("reviewArea"), review.get("reviewArea")), cb.equal(later.get("contentRevision"), review.get("contentRevision")),
                cb.isNull(later.get("invalidatedAt")), cb.or(cb.greaterThan(later.<Instant>get("reviewedAt"), review.<Instant>get("reviewedAt")),
                        cb.and(cb.equal(later.get("reviewedAt"), review.get("reviewedAt")), cb.greaterThan(later.<UUID>get("id"), review.<UUID>get("id")))));
        approved.select(review.get("id")).where(cb.equal(review.get("exerciseVersionId"), version.get("id")), cb.equal(review.get("reviewArea"), area),
                cb.equal(review.get("decision"), "APPROVED"), cb.isNull(review.get("invalidatedAt")),
                cb.equal(review.get("contentRevision"), version.get("contentRevision")), cb.not(cb.exists(newer)));
        return cb.exists(approved);
    }

    private List<Order> order(CriteriaBuilder cb, CriteriaQuery<?> query, Root<ExerciseVersion> version, Root<Exercise> exercise,
                              ExerciseReviewQueueSort sort) {
        Expression<String> name = cb.lower(exercise.get("canonicalName"));
        return switch (sort) {
            case NEWEST -> List.of(cb.desc(version.get("createdAt")), cb.asc(name), cb.asc(version.get("id")));
            case NAME -> List.of(cb.asc(name), cb.asc(version.get("id")));
            case ACTION_NEEDED -> List.of(cb.asc(cb.selectCase().when(actionNeeded(cb, ready(cb, query, version), version), 0).otherwise(1)),
                    cb.desc(version.get("createdAt")), cb.asc(name), cb.asc(version.get("id")));
        };
    }
}
