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
 * Read model for the editorial worklist.  It deliberately contains no review or publishing rules.
 */
@Service
@RequiredArgsConstructor
public class ExerciseReviewQueryService {
    private final JdbcTemplate jdbc;
    private final ExerciseEditorialWorkflowService workflow;

    @Transactional(readOnly = true)
    public QueuePage items(UUID batchId, String query, String status, Boolean readyToPublish,
                           Boolean hasErrors, Boolean hasBlockers, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid page size");
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        List<QueueRow> rows = jdbc.query("""
                SELECT v.id,e.id,e.canonical_name,v.version_number,v.status,v.version,v.created_at,
                       r.batch_id,r.row_number,r.source_record_key,r.status,
                       COUNT(i.id) FILTER (WHERE i.resolved_at IS NULL AND i.severity='ERROR'),
                       COUNT(i.id) FILTER (WHERE i.resolved_at IS NULL AND i.severity='WARNING'),
                       COUNT(i.id) FILTER (WHERE i.resolved_at IS NULL AND i.severity='BLOCKER')
                  FROM exercise_catalog.exercise_version v
                  JOIN exercise_catalog.exercise e ON e.id=v.exercise_id
                  LEFT JOIN exercise_import.import_record r ON r.draft_version_id=v.id
                  LEFT JOIN exercise_import.import_issue i ON i.record_id=r.id
                 WHERE (? IS NULL OR r.batch_id=?) AND (? IS NULL OR LOWER(e.canonical_name) LIKE ?)
                   AND (? IS NULL OR v.status=?)
                 GROUP BY v.id,e.id,e.canonical_name,v.version_number,v.status,v.version,v.created_at,
                          r.batch_id,r.row_number,r.source_record_key,r.status
                 ORDER BY CASE WHEN v.status IN ('DRAFT','CHANGES_REQUESTED','IN_REVIEW') THEN 0 ELSE 1 END,
                          v.created_at DESC, LOWER(e.canonical_name), v.id
                 LIMIT ? OFFSET ?
                """, (rs, n) -> new QueueRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getInt(4), rs.getString(5), rs.getLong(6), rs.getObject(7, Instant.class),
                rs.getObject(8, UUID.class), rs.getObject(9, Long.class), rs.getString(10), rs.getString(11),
                rs.getInt(12), rs.getInt(13), rs.getInt(14)), batchId, batchId, q, q, status, status, size, page * size);
        List<ExerciseReviewQueueItem> content = rows.stream().map(row -> item(row)).filter(item ->
                (readyToPublish == null || readyToPublish == item.readyToPublish())
                        && (hasErrors == null || hasErrors == (item.errorCount() > 0))
                        && (hasBlockers == null || hasBlockers == (item.blockerCount() > 0))).toList();
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exercise_catalog.exercise_version v JOIN exercise_catalog.exercise e ON e.id=v.exercise_id
                LEFT JOIN exercise_import.import_record r ON r.draft_version_id=v.id
                WHERE (? IS NULL OR r.batch_id=?) AND (? IS NULL OR LOWER(e.canonical_name) LIKE ?) AND (? IS NULL OR v.status=?)
                """, Long.class, batchId, batchId, q, q, status, status);
        return new QueuePage(content, page, size, total == null ? 0 : total);
    }

    private ExerciseReviewQueueItem item(QueueRow row) {
        var review = workflow.status(row.versionId);
        List<String> completed = List.of("CONTENT", "TECHNIQUE", "ANATOMY_EXPOSURE", "LICENSE", "MEDIA").stream()
                .filter(area -> !review.unmetRequirements().contains("REVIEW_" + area + "_REQUIRED")).toList();
        List<String> missing = review.unmetRequirements().stream().filter(value -> value.startsWith("REVIEW_")).toList();
        return new ExerciseReviewQueueItem(row.exerciseId, row.versionId, row.versionNumber, row.canonicalName,
                row.sourceRecordKey, row.batchId, row.rowNumber, row.versionStatus, row.recordStatus, row.errors,
                row.warnings, row.blockers, completed, missing, review.unmetRequirements().isEmpty(), row.updatedAt, row.version);
    }

    private record QueueRow(UUID versionId, UUID exerciseId, String canonicalName, int versionNumber,
                            String versionStatus,
                            long version, Instant updatedAt, UUID batchId, Long rowNumber, String sourceRecordKey,
                            String recordStatus, int errors, int warnings, int blockers) {
    }

    public record ExerciseReviewQueueItem(UUID exerciseId, UUID exerciseVersionId, int versionNumber,
                                          String canonicalName,
                                          String sourceRecordKey, UUID batchId, Long importRowNumber,
                                          String versionStatus, String importRecordStatus,
                                          int errorCount, int warningCount, int blockerCount,
                                          List<String> completedReviewAreas,
                                          List<String> missingReviewAreas, boolean readyToPublish, Instant updatedAt,
                                          long expectedVersion) {
    }

    public record QueuePage(List<ExerciseReviewQueueItem> content, int page, int size, long totalElements) {
        public int totalPages() {
            return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        }
    }
}
