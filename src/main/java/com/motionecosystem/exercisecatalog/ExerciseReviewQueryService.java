package com.motionecosystem.exercisecatalog;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bounded SQL read model for the editorial worklist.  Publishing and review decisions remain in
 * {@link ExerciseEditorialWorkflowService}; this projection deliberately does not invoke it once
 * per row.
 */
@Service
@RequiredArgsConstructor
public class ExerciseReviewQueryService {
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    private final ExerciseEditorialWorkflowService workflow;

    /**
     * Editorial detail deliberately composes existing catalog/editorial projections.  It exposes
     * import problems as human-facing fields, but never staging raw or normalized JSON.
     */
    @Transactional(readOnly = true)
    public EditorialDetail detail(UUID versionId) {
        CatalogService.EditorView editor = catalog.editor(versionId);
        var review = workflow.status(versionId);
        var diff = workflow.diff(versionId);
        ImportMetadata metadata = jdbc.query("""
                SELECT record.batch_id,record.row_number,record.source_record_key,record.status
                  FROM exercise_import.import_record record WHERE record.draft_version_id=?
                """, rs -> rs.next() ? new ImportMetadata(rs.getObject(1, UUID.class), rs.getObject(2, Long.class),
                rs.getString(3), rs.getString(4)) : null, versionId);
        List<ImportProblem> problems = jdbc.query("""
                SELECT issue.severity,issue.code,issue.json_pointer,issue.message,issue.resolved_at
                  FROM exercise_import.import_issue issue JOIN exercise_import.import_record record ON record.id=issue.record_id
                 WHERE record.draft_version_id=? ORDER BY issue.severity,issue.code,issue.id
                """, (rs, n) -> new ImportProblem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, Instant.class)), versionId);
        boolean readyToPublish = "APPROVED".equals(review.status()) && review.unmetRequirements().isEmpty();
        boolean actionNeeded = !readyToPublish && !List.of("PUBLISHED", "WITHDRAWN").contains(review.status());
        return new EditorialDetail(editor, metadata, problems, review, diff, readyToPublish, actionNeeded);
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
        String order = orderBy(sort);
        Object[] parameters = {batchId, batchId, q, q, normalizedStatus, normalizedStatus,
                readyToPublish, readyToPublish, actionNeeded, actionNeeded, hasErrors, hasErrors,
                hasBlockers, hasBlockers, area, area};
        String filtered = filteredSql();
        List<ExerciseReviewQueueItem> content = jdbc.query(filtered + """
                 SELECT exercise_id, version_id, version_number, canonical_name, source_record_key, batch_id,
                        row_number, version_status, record_status, error_count, warning_count, blocker_count,
                        completed_areas, missing_areas, ready_to_publish, action_needed, updated_at, expected_version
                   FROM filtered
                 ORDER BY %s
                 LIMIT ? OFFSET ?
                """.formatted(order), (rs, n) -> new ExerciseReviewQueueItem(
                rs.getObject("exercise_id", UUID.class), rs.getObject("version_id", UUID.class),
                rs.getInt("version_number"), rs.getString("canonical_name"), rs.getString("source_record_key"),
                rs.getObject("batch_id", UUID.class), rs.getObject("row_number", Long.class),
                rs.getString("version_status"), rs.getString("record_status"), rs.getInt("error_count"),
                rs.getInt("warning_count"), rs.getInt("blocker_count"),
                stringArray(rs.getArray("completed_areas")), stringArray(rs.getArray("missing_areas")),
                rs.getBoolean("ready_to_publish"), rs.getBoolean("action_needed"), rs.getObject("updated_at", Instant.class),
                rs.getLong("expected_version")), append(parameters, size, page * size));
        Long total = jdbc.queryForObject(filtered + " SELECT COUNT(*) FROM filtered", Long.class, parameters);
        return new QueuePage(content, page, size, total == null ? 0 : total);
    }

    private static String filteredSql() {
        return """
                WITH queue AS (
                  SELECT v.id version_id, e.id exercise_id, e.canonical_name, v.version_number,
                         v.status version_status, v.version expected_version, v.created_at updated_at,
                         r.batch_id, r.row_number, r.source_record_key, r.status record_status,
                         COALESCE(issues.error_count,0)::int error_count, COALESCE(issues.warning_count,0)::int warning_count,
                         COALESCE(issues.blocker_count,0)::int blocker_count,
                         COALESCE(review.completed_areas, ARRAY[]::text[]) completed_areas,
                         COALESCE(review.missing_areas, ARRAY[]::text[]) missing_areas,
                         (v.status='APPROVED' AND COALESCE(issues.error_count,0)=0 AND COALESCE(issues.blocker_count,0)=0
                          AND COALESCE(review.missing_areas, ARRAY[]::text[])=ARRAY[]::text[]
                          AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_load_characteristic l WHERE l.exercise_version_id=v.id)
                          AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_contribution c WHERE c.exercise_version_id=v.id)
                          AND EXISTS (SELECT 1 FROM exercise_catalog.evidence_source es WHERE es.exercise_version_id=v.id)) ready_to_publish,
                         (v.status NOT IN ('PUBLISHED','WITHDRAWN') AND NOT
                          (v.status='APPROVED' AND COALESCE(issues.error_count,0)=0 AND COALESCE(issues.blocker_count,0)=0
                           AND COALESCE(review.missing_areas, ARRAY[]::text[])=ARRAY[]::text[]
                           AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_load_characteristic l WHERE l.exercise_version_id=v.id)
                           AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_contribution c WHERE c.exercise_version_id=v.id)
                           AND EXISTS (SELECT 1 FROM exercise_catalog.evidence_source es WHERE es.exercise_version_id=v.id))) action_needed
                    FROM exercise_catalog.exercise_version v
                    JOIN exercise_catalog.exercise e ON e.id=v.exercise_id
                    LEFT JOIN exercise_import.import_record r ON r.draft_version_id=v.id
                    LEFT JOIN LATERAL (
                      SELECT COUNT(*) FILTER (WHERE resolved_at IS NULL AND severity='ERROR') error_count,
                             COUNT(*) FILTER (WHERE resolved_at IS NULL AND severity='WARNING') warning_count,
                             COUNT(*) FILTER (WHERE resolved_at IS NULL AND severity='BLOCKER') blocker_count
                        FROM exercise_import.import_issue WHERE record_id=r.id
                    ) issues ON true
                    LEFT JOIN LATERAL (
                      SELECT array_agg(area ORDER BY area) FILTER (WHERE decision='APPROVED') completed_areas,
                             array_agg(area ORDER BY area) FILTER (WHERE decision IS DISTINCT FROM 'APPROVED') missing_areas
                        FROM (
                          SELECT required.area, latest.decision
                            FROM (VALUES ('CONTENT'),('TECHNIQUE'),('ANATOMY_EXPOSURE'),('LICENSE')) required(area)
                            LEFT JOIN LATERAL (
                              SELECT decision FROM exercise_catalog.exercise_review er
                               WHERE er.exercise_version_id=v.id AND er.review_area=required.area AND er.invalidated_at IS NULL
                                 AND er.content_revision=v.content_revision
                               ORDER BY er.reviewed_at DESC, er.id DESC LIMIT 1
                            ) latest ON true
                          UNION ALL
                          SELECT 'MEDIA', latest.decision
                            FROM LATERAL (SELECT EXISTS (SELECT 1 FROM exercise_catalog.exercise_media em WHERE em.exercise_version_id=v.id) media_required) m
                            LEFT JOIN LATERAL (SELECT decision FROM exercise_catalog.exercise_review er WHERE er.exercise_version_id=v.id AND er.review_area='MEDIA' AND er.invalidated_at IS NULL AND er.content_revision=v.content_revision ORDER BY er.reviewed_at DESC,er.id DESC LIMIT 1) latest ON m.media_required
                           WHERE m.media_required
                        ) requirements(area, decision)
                    ) review ON true
                ), filtered AS (
                  SELECT * FROM queue WHERE (? IS NULL OR batch_id=?) AND (? IS NULL OR lower(canonical_name) LIKE ?)
                    AND (? IS NULL OR version_status=?) AND (? IS NULL OR ready_to_publish=?)
                    AND (? IS NULL OR action_needed=?)
                    AND (? IS NULL OR (error_count > 0)=?) AND (? IS NULL OR (blocker_count > 0)=?)
                    AND (? IS NULL OR ? = ANY(missing_areas))
                )
                """;
    }

    private static Object[] append(Object[] base, Object... tail) {
        Object[] result = java.util.Arrays.copyOf(base, base.length + tail.length);
        System.arraycopy(tail, 0, result, base.length, tail.length);
        return result;
    }
    private static List<String> stringArray(java.sql.Array value) throws java.sql.SQLException {
        if (value == null) return List.of();
        Object raw = value.getArray();
        return raw instanceof String[] strings ? List.of(strings) : List.of();
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
    private static String orderBy(String sort) {
        String value = sort == null || sort.isBlank() ? "default" : sort.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "default", "action-needed" -> "CASE WHEN action_needed THEN 0 ELSE 1 END, updated_at DESC, lower(canonical_name), version_id";
            case "newest" -> "updated_at DESC, lower(canonical_name), version_id";
            case "name" -> "lower(canonical_name), version_id";
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
    public record EditorialDetail(CatalogService.EditorView editor, ImportMetadata importMetadata,
                                  List<ImportProblem> importProblems,
                                  com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion.ReviewResult review,
                                  ExerciseEditorialWorkflowService.VersionDiff diff,
                                  boolean readyToPublish, boolean actionNeeded) {}
}
