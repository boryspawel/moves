package com.motionecosystem.exercisecatalog;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model for the editorial worklist. Publishing and review decisions remain in
 * {@link ExerciseEditorialWorkflowService}; this projection deliberately does not invoke it once
 * per row.
 */
@Service
@RequiredArgsConstructor
public class ExerciseReviewQueryService {
    private final ExerciseReviewQueueRepository queue;
    private final ImportRecordReviewRepository records;
    private final ImportIssueReviewRepository issues;
    private final CatalogService catalog;
    private final ExerciseEditorialWorkflowService workflow;
    private final ExerciseContributionRepository contributions;
    private final AnatomyReferenceQueryPort anatomy;

    /**
     * Editorial detail deliberately composes existing catalog/editorial projections.  It exposes
     * import problems as human-facing fields, but never staging raw or normalized JSON.
     */
    @Transactional(readOnly = true)
    public EditorialDetail detail(UUID versionId) {
        CatalogService.EditorView editor = catalog.editor(versionId);
        var review = workflow.status(versionId);
        var diff = workflow.diff(versionId);
        ImportRecord record = records.findByDraftVersionId(versionId).orElse(null);
        ImportMetadata metadata = record == null ? null : new ImportMetadata(record.batchId, record.rowNumber,
                record.sourceRecordKey, record.status);
        List<ImportProblem> problems = record == null ? List.of() : issues.findByRecordIdOrderBySeverityAscCodeAscIdAsc(record.id).stream()
                .map(issue -> new ImportProblem(issue.severity, issue.code, issue.jsonPointer, issue.message, issue.resolvedAt)).toList();
        List<ExerciseContribution> contributionItems = contributions.findByExerciseVersionIdOrderById(versionId);
        Map<java.util.UUID, AnatomyReferenceQueryPort.AnatomicalStructureSnapshot> anatomyById = anatomy.findStructures(
                contributionItems.stream().map(item -> item.anatomicalStructureId).collect(java.util.stream.Collectors.toSet()));
        List<AnatomyContributionSnapshot> anatomyContributions = contributionItems.stream().map(item -> {
            AnatomyReferenceQueryPort.AnatomicalStructureSnapshot structure = anatomyById.get(item.anatomicalStructureId);
            if (structure == null) throw new IllegalStateException("contribution references missing anatomy");
            return new AnatomyContributionSnapshot(structure.code(), structure.displayName(), structure.type().name(),
                    item.role.name(), item.loadChannel.name(), item.contributionBand.name(), item.confidenceClass,
                    item.evidenceGrade);
        }).toList();
        boolean readyToPublish = workflow.readyToPublish(versionId);
        boolean actionNeeded = !readyToPublish && !List.of("PUBLISHED", "WITHDRAWN").contains(review.status());
        return new EditorialDetail(editor, metadata, problems, review, diff, anatomyContributions, readyToPublish, actionNeeded);
    }

    @Transactional(readOnly = true)
    public QueuePage items(UUID batchId, String query, String status, Boolean readyToPublish,
                           Boolean actionNeeded, Boolean hasErrors, Boolean hasBlockers, String missingReviewArea,
                           String sort, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid page size");
        }
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String normalizedStatus = optionalEnum(status, ExerciseVersionStatus.class, "status");
        String area = optionalArea(missingReviewArea);
        ExerciseReviewQueueSort order = orderBy(sort);
        return queue.findQueue(new ExerciseReviewQueueFilter(batchId, q, normalizedStatus == null ? null : ExerciseVersionStatus.valueOf(normalizedStatus),
                readyToPublish, actionNeeded, hasErrors, hasBlockers, area, order, page, size));
    }

    private static String optionalArea(String value) {
        if (value == null || value.isBlank()) return null;
        String area = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE", "MEDIA").contains(area))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid missing review area");
        return area;
    }
    private static <E extends Enum<E>> String optionalEnum(String value, Class<E> type, String field) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT)).name(); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid " + field); }
    }
    static ExerciseReviewQueueSort orderBy(String sort) {
        String value = sort == null || sort.isBlank() ? "default" : sort.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "default", "action-needed" -> ExerciseReviewQueueSort.ACTION_NEEDED;
            case "newest" -> ExerciseReviewQueueSort.NEWEST;
            case "name" -> ExerciseReviewQueueSort.NAME;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sort");
        };
    }

    public record ExerciseReviewQueueItem(UUID exerciseId, UUID exerciseVersionId, int versionNumber, String canonicalName,
                                          String sourceRecordKey, UUID batchId, Long importRowNumber, String versionStatus,
                                          String importRecordStatus, int errorCount, int warningCount, int blockerCount,
                                          List<String> completedReviewAreas, List<String> missingReviewAreas,
                                          boolean readyToPublish, boolean actionNeeded, Instant updatedAt, long expectedVersion) {}
    public record QueuePage(List<ExerciseReviewQueueItem> content, int page, int size, long totalElements) {
        public int totalPages() { return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size); }
    }
    public record ImportMetadata(UUID batchId, Long rowNumber, String sourceRecordKey, String recordStatus) {}
    public record ImportProblem(String severity, String code, String field, String message, Instant resolvedAt) {}
    public record AnatomyContributionSnapshot(String code, String displayName, String structureType, String role,
                                             String loadChannel, String contributionBand, String confidenceClass,
                                             String evidenceGrade) {}
    public record EditorialDetail(CatalogService.EditorView editor, ImportMetadata importMetadata,
                                  List<ImportProblem> importProblems,
                                  com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion.ReviewResult review,
                                  ExerciseEditorialWorkflowService.VersionDiff diff,
                                  List<AnatomyContributionSnapshot> anatomyContributions,
                                  boolean readyToPublish, boolean actionNeeded) {}
}
