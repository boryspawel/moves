package com.motionecosystem.exerciseimport;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exerciseimport.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ExerciseImportService {
    private static final Set<String> MEDIA_TYPES = Set.of(
            "application/x-ndjson", "application/jsonl", "application/json", "text/plain",
            "application/octet-stream");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final ImportArtifactStorage artifacts;
    private final ExerciseImportJobLauncher jobs;
    private final NormalizeImportRecord normalizer;
    private final ValidateImportRecord validator;
    private final FindExerciseMatch matcher;
    private final CreateExerciseDraft drafts;
    private final AuditRecorder audit;
    @Value("${exercise-import.limits.file-bytes}") private long maximumFileBytes;

    SourceView createSource(String actor, CreateSource request) {
        if (request == null) throw badRequest("source is required");
        String code = code(request.code(), 80);
        String name = text(request.displayName(), 160, "display name");
        String locale = normalizeLocale(request.defaultLocale());
        String license = text(request.licenseCode(), 120, "license code");
        UUID id = UUID.randomUUID(); Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO exercise_import.import_source(
                        id,code,display_name,default_locale,license_code,license_verified,active,
                        created_at,created_by_subject,version) VALUES (?,?,?,?,?,?,TRUE,?,?,0)
                    """, id, code, name, locale, license, request.licenseVerified(), Timestamp.from(now), actor);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "source code already exists", duplicate);
        }
        audit.record(actor, "EXERCISE_IMPORT_SOURCE_CREATED", "ImportSource", id);
        return source(id);
    }

    List<SourceView> sources() {
        return jdbc.query("""
                SELECT id,code,display_name,default_locale,license_code,license_verified,active,created_at,version
                  FROM exercise_import.import_source ORDER BY code,id
                """, (rs, n) -> new SourceView(rs.getObject(1, UUID.class),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getBoolean(7),
                instant(rs,8),rs.getLong(9)));
    }

    BatchView upload(String actor, UUID sourceId, String requestKey, boolean force, MultipartFile file) {
        if (sourceId == null || file == null || file.isEmpty()) throw badRequest("sourceId and a non-empty JSONL file are required");
        String key = text(requestKey, 160, "Idempotency-Key");
        BatchView existingRequest = batchByRequest(sourceId, key);
        if (existingRequest != null) return existingRequest;
        String mediaType = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!MEDIA_TYPES.contains(mediaType)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "expected JSONL media type");
        requireSource(sourceId);
        UUID artifactId = UUID.randomUUID();
        ImportArtifactStorage.StoredArtifact stored;
        try (InputStream input = file.getInputStream()) {
            stored = artifacts.store(artifactId, safeFilename(file.getOriginalFilename()), input, maximumFileBytes);
        } catch (FileSystemImportArtifactStorage.ArtifactTooLargeException tooLarge) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, tooLarge.getMessage());
        } catch (IOException storageFailure) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "cannot persist import artifact");
        }
        UUID previous = jdbc.query("""
                SELECT batch.id FROM exercise_import.import_batch batch
                JOIN exercise_import.import_artifact artifact ON artifact.batch_id=batch.id
                WHERE batch.source_id=? AND artifact.sha256=? ORDER BY batch.submitted_at,batch.id LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, sourceId, stored.sha256());
        if (previous != null && !force) {
            deleteTransientArtifact(stored.storageKey());
            return batch(previous);
        }
        if (force && previous == null) {
            deleteTransientArtifact(stored.storageKey());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "forceReprocess requires a previous identical batch");
        }
        UUID batchId = UUID.randomUUID(); Instant now = clock.instant();
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO exercise_import.import_batch(
                            id,source_id,request_key,status,forced_from_batch_id,submitted_by_subject,submitted_at,version)
                        VALUES (?,?,?,'QUEUED',?,?,?,0)
                        """, batchId, sourceId, key, force ? previous : null, actor, Timestamp.from(now));
                jdbc.update("""
                        INSERT INTO exercise_import.import_artifact(
                            id,batch_id,storage_key,original_filename,media_type,byte_size,sha256,created_at)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, artifactId, batchId, stored.storageKey(), safeFilename(file.getOriginalFilename()),
                        mediaType, stored.byteSize(), stored.sha256(), Timestamp.from(now));
                audit.record(actor, force ? "EXERCISE_IMPORT_FORCE_REPROCESS_REQUESTED" : "EXERCISE_IMPORT_BATCH_RECEIVED",
                        "ImportBatch", batchId);
            });
        } catch (RuntimeException failure) {
            deleteTransientArtifact(stored.storageKey());
            BatchView raced = batchByRequest(sourceId, key);
            if (raced != null) return raced;
            throw failure;
        }
        jobs.launch(batchId);
        return batch(batchId);
    }

    void restart(UUID batchId) {
        BatchView batch = batch(batchId);
        if (!batch.status.equals("FAILED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "only a failed batch can be restarted");
        jdbc.update("UPDATE exercise_import.import_batch SET status='QUEUED',completed_at=NULL,version=version+1 WHERE id=?", batchId);
        jobs.launch(batchId);
    }

    BatchView batch(UUID id) {
        BatchView result = jdbc.query("""
                SELECT batch.id,batch.source_id,batch.request_key,batch.status,batch.forced_from_batch_id,
                       batch.submitted_by_subject,batch.submitted_at,batch.started_at,batch.completed_at,
                       batch.total_count,batch.valid_count,batch.invalid_count,batch.blocked_count,
                       batch.drafted_count,batch.unchanged_count,batch.version,
                       artifact.original_filename,artifact.media_type,artifact.byte_size,artifact.sha256
                  FROM exercise_import.import_batch batch
                  JOIN exercise_import.import_artifact artifact ON artifact.batch_id=batch.id WHERE batch.id=?
                """, rs -> rs.next() ? batchView(rs) : null, id);
        if (result == null) throw notFound("import batch not found");
        return result;
    }

    RecordPage records(UUID batchId, String status, String severity, int page, int size) {
        page(page, size); batch(batchId);
        String normalizedStatus = blankToNull(status); String normalizedSeverity = blankToNull(severity);
        String where = " WHERE record.batch_id=? AND (CAST(? AS varchar) IS NULL OR record.status=?) AND (CAST(? AS varchar) IS NULL OR EXISTS "
                + "(SELECT 1 FROM exercise_import.import_issue issue WHERE issue.record_id=record.id AND issue.severity=?)) ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record record" + where,
                Long.class, batchId, normalizedStatus, normalizedStatus, normalizedSeverity, normalizedSeverity);
        List<RecordSummary> content = jdbc.query("""
                SELECT record.id,record.row_number,record.source_record_key,record.status,record.raw_sha256,
                       record.normalized_sha256,record.matched_exercise_id,record.draft_version_id,record.version
                  FROM exercise_import.import_record record
                """ + where + " ORDER BY record.row_number,record.id LIMIT ? OFFSET ?", (rs,n) ->
                new RecordSummary(rs.getObject(1,UUID.class),rs.getLong(2),rs.getString(3),rs.getString(4),
                        rs.getString(5),rs.getString(6),rs.getObject(7,UUID.class),rs.getObject(8,UUID.class),rs.getLong(9)),
                batchId, normalizedStatus, normalizedStatus, normalizedSeverity, normalizedSeverity, size, page * size);
        return new RecordPage(content,page,size,total == null ? 0 : total);
    }

    RecordDetail record(UUID id) {
        RecordDetail result = jdbc.query("""
                SELECT record.id,record.batch_id,record.row_number,record.source_record_key,record.status,
                       record.raw_payload::text,record.normalized_payload::text,record.raw_sha256,
                       record.normalized_sha256,record.normalization_version,record.matched_exercise_id,
                       record.draft_version_id,record.created_at,record.updated_at,record.version
                  FROM exercise_import.import_record record WHERE record.id=?
                """, rs -> {
            if (!rs.next()) return null;
            return new RecordDetail(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),
                    rs.getString(4),rs.getString(5),parse(rs.getString(6)),parseNullable(rs.getString(7)),
                    rs.getString(8),rs.getString(9),rs.getString(10),rs.getObject(11,UUID.class),
                    rs.getObject(12,UUID.class),instant(rs,13),instant(rs,14),
                    rs.getLong(15),issues(id),candidates(id));
        }, id);
        if (result == null) throw notFound("import record not found");
        return result;
    }

    RecordDetail decideMatch(String actor, UUID recordId, MatchDecision request) {
        if (request == null || request.candidateId() == null || request.decision() == null) throw badRequest("candidateId and decision are required");
        String decision = request.decision().toUpperCase(Locale.ROOT);
        if (!Set.of("SAME","DIFFERENT","UNSURE").contains(decision)) throw badRequest("decision must be SAME, DIFFERENT or UNSURE");
        transactions.executeWithoutResult(status -> {
            UUID exercise = jdbc.query("SELECT exercise_id FROM exercise_import.import_match_candidate WHERE id=? AND record_id=? FOR UPDATE",
                    rs -> rs.next() ? rs.getObject(1,UUID.class) : null, request.candidateId(), recordId);
            if (exercise == null) throw notFound("match candidate not found");
            jdbc.update("UPDATE exercise_import.import_match_candidate SET decision=?,decided_by_subject=?,decided_at=?,version=version+1 WHERE id=?",
                    decision,actor,Timestamp.from(clock.instant()),request.candidateId());
            if (decision.equals("SAME")) {
                jdbc.update("UPDATE exercise_import.import_record SET matched_exercise_id=?,status='READY_FOR_DRAFT',updated_at=?,version=version+1 WHERE id=? AND draft_version_id IS NULL",
                        exercise,Timestamp.from(clock.instant()),recordId);
            } else if (decision.equals("UNSURE")) {
                jdbc.update("UPDATE exercise_import.import_record SET status='MATCH_CANDIDATES',updated_at=?,version=version+1 WHERE id=? AND draft_version_id IS NULL",
                        Timestamp.from(clock.instant()),recordId);
            } else {
                Boolean remaining = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM exercise_import.import_match_candidate WHERE record_id=? AND decision IS DISTINCT FROM 'DIFFERENT')",
                        Boolean.class,recordId);
                if (!Boolean.TRUE.equals(remaining))
                    jdbc.update("UPDATE exercise_import.import_record SET matched_exercise_id=NULL,status='READY_FOR_DRAFT',updated_at=?,version=version+1 WHERE id=? AND draft_version_id IS NULL",
                        Timestamp.from(clock.instant()),recordId);
            }
            audit.record(actor,"EXERCISE_IMPORT_MATCH_DECIDED","ImportRecord",recordId);
        });
        UUID batchId = jdbc.queryForObject("SELECT batch_id FROM exercise_import.import_record WHERE id=?",UUID.class,recordId);
        if (readyForDraft(recordId)) {
            try {
                // Use the same idempotent catalog boundary as the batch step.  The
                // matching decision remains durable even if catalog draft creation
                // temporarily fails, so the record stays visible for a safe retry.
                createDraft(actor, recordId);
            } catch (RuntimeException failure) {
                draftIssue(batchId, recordId);
                audit.record(actor, "EXERCISE_IMPORT_DRAFT_CREATION_FAILED", "ImportRecord", recordId);
            }
        }
        if(batchId!=null) refresh(batchId);
        return record(recordId);
    }

    MappingView decideMapping(String actor, UUID mappingId, MappingDecision request) {
        if (request == null || request.decision() == null) throw badRequest("mapping decision is required");
        String decision = request.decision().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVED","REJECTED").contains(decision)) throw badRequest("decision must be APPROVED or REJECTED");
        MappingView before = mapping(mappingId);
        String canonical = decision.equals("APPROVED") ? code(request.canonicalValue(),240) : null;
        if (canonical != null && !canonicalExists(before.dictionaryType,canonical)) throw badRequest("canonical dictionary value does not exist");
        jdbc.update("UPDATE exercise_import.import_mapping SET status=?,canonical_value=?,decided_by_subject=?,decided_at=?,version=version+1 WHERE id=?",
                decision,canonical,actor,Timestamp.from(clock.instant()),mappingId);
        audit.record(actor,"EXERCISE_IMPORT_MAPPING_DECIDED","ImportMapping",mappingId);
        if (decision.equals("APPROVED")) {
            List<UUID> blocked = jdbc.queryForList("""
                    SELECT record.id FROM exercise_import.import_record record
                    JOIN exercise_import.import_batch batch ON batch.id=record.batch_id
                    WHERE batch.source_id=? AND record.status='BLOCKED_BY_MAPPING'
                      AND record.raw_payload::text ILIKE ? ORDER BY record.id
                    """,UUID.class,before.sourceId,"%"+before.sourceValue+"%");
            for (UUID id : blocked) {
                jdbc.update("DELETE FROM exercise_import.import_issue WHERE record_id=? AND code='MAPPING_REQUIRED'",id);
                jdbc.update("UPDATE exercise_import.import_record SET status='PARSED',normalized_payload=NULL,normalized_sha256=NULL,normalization_version=NULL,updated_at=?,version=version+1 WHERE id=?",
                        Timestamp.from(clock.instant()),id);
                normalizer.normalize(id); validator.validate(id); matcher.findMatches(id);
            }
            jdbc.queryForList("SELECT id FROM exercise_import.import_batch WHERE source_id=?",UUID.class,before.sourceId)
                    .forEach(this::refresh);
        }
        return mapping(mappingId);
    }

    UUID createDraft(String actor, UUID recordId) {
        UUID versionId = drafts.createDraft(recordId,actor);
        UUID batchId = jdbc.queryForObject("SELECT batch_id FROM exercise_import.import_record WHERE id=?",UUID.class,recordId);
        if(batchId!=null)refresh(batchId);
        return versionId;
    }

    String exportIssues(UUID batchId, String severity, boolean csv) {
        batch(batchId); String filter=blankToNull(severity);
        List<IssueView> items=jdbc.query("""
                SELECT issue.id,issue.record_id,issue.row_number,record.source_record_key,
                       issue.code,issue.stage,issue.severity,issue.json_pointer,issue.message,
                       issue.created_at,issue.resolved_at
                FROM exercise_import.import_issue issue
                LEFT JOIN exercise_import.import_record record ON record.id=issue.record_id
                WHERE issue.batch_id=? AND (CAST(? AS varchar) IS NULL OR issue.severity=CAST(? AS varchar))
                ORDER BY COALESCE(issue.row_number,0),issue.severity,issue.code,issue.id
                """,(rs,n)->issueView(rs),batchId,filter,filter);
        if (csv) {
            StringBuilder value=new StringBuilder("rowNumber,sourceRecordKey,severity,stage,code,jsonPointer,message\n");
            items.forEach(i->value.append(i.rowNumber==null?"":i.rowNumber).append(',').append(csv(i.sourceRecordKey)).append(',').append(i.severity).append(',')
                    .append(i.stage).append(',').append(csv(i.code)).append(',').append(csv(i.jsonPointer)).append(',')
                    .append(csv(i.message)).append('\n'));
            return value.toString();
        }
        return items.stream().map(item->json.writeValueAsString(item)).reduce("",(a,b)->a+b+"\n");
    }

    private SourceView source(UUID id) {
        SourceView source=jdbc.query("SELECT id,code,display_name,default_locale,license_code,license_verified,active,created_at,version FROM exercise_import.import_source WHERE id=?",
                rs->rs.next()?new SourceView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getBoolean(7),instant(rs,8),rs.getLong(9)):null,id);
        if(source==null) throw notFound("import source not found"); return source;
    }
    private void requireSource(UUID id){ source(id); }
    private BatchView batchByRequest(UUID sourceId,String key){return jdbc.query("SELECT id FROM exercise_import.import_batch WHERE source_id=? AND request_key=?",rs->rs.next()?batch(rs.getObject(1,UUID.class)):null,sourceId,key);}
    private List<IssueView> issues(UUID id){return jdbc.query("SELECT issue.id,issue.record_id,issue.row_number,record.source_record_key,issue.code,issue.stage,issue.severity,issue.json_pointer,issue.message,issue.created_at,issue.resolved_at FROM exercise_import.import_issue issue JOIN exercise_import.import_record record ON record.id=issue.record_id WHERE issue.record_id=? ORDER BY issue.severity,issue.code,issue.id",(rs,n)->issueView(rs),id);}

    private List<CandidateView> candidates(UUID id) {
        return jdbc.query("""
                SELECT candidate.id,candidate.exercise_id,exercise.canonical_name,candidate.rank,candidate.score,
                       candidate.reasons::text,candidate.algorithm_version,candidate.decision,
                       candidate.decided_by_subject,candidate.decided_at,candidate.version
                  FROM exercise_import.import_match_candidate candidate
                  JOIN exercise_catalog.exercise exercise ON exercise.id=candidate.exercise_id
                 WHERE candidate.record_id=? ORDER BY candidate.rank,candidate.id
                """, (rs, n) -> new CandidateView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5), parse(rs.getString(6)), rs.getString(7), rs.getString(8), rs.getString(9), instant(rs, 10), rs.getLong(11)), id);
    }
    private MappingView mapping(UUID id){MappingView value=jdbc.query("SELECT id,source_id,dictionary_type,source_value,canonical_value,status,decided_by_subject,decided_at,version FROM exercise_import.import_mapping WHERE id=?",rs->rs.next()?new MappingView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),instant(rs,8),rs.getLong(9)):null,id);if(value==null)throw notFound("mapping not found");return value;}
    private boolean canonicalExists(String type,String value){String table=switch(type){case"EQUIPMENT"->"exercise_catalog.exercise_equipment_dictionary";case"POSITION"->"exercise_catalog.exercise_position_dictionary";case"DOSE_UNIT"->"exercise_catalog.dose_unit_dictionary";default->null;};return table!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE code=? AND active)",Boolean.class,value));}
    private JsonNode parse(String value){return json.readTree(value);} private JsonNode parseNullable(String value){return value==null?null:parse(value);}
    private void deleteTransientArtifact(String key){try{artifacts.delete(key);}catch(IOException ignored){}}

    private void refresh(UUID batchId) {
        jdbc.update("""
                UPDATE exercise_import.import_batch batch SET
                    total_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=?),
                    valid_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status NOT IN ('INVALID','BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','REJECTED')),
                    invalid_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status='INVALID'),
                    blocked_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND (r.status IN ('BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','MATCH_CANDIDATES') OR EXISTS (SELECT 1 FROM exercise_import.import_issue i WHERE i.record_id=r.id AND i.code='DRAFT_CREATION_FAILED' AND i.resolved_at IS NULL))),
                    drafted_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status='DRAFTED'),
                    unchanged_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status='UNCHANGED'),
                    status=CASE WHEN batch.status='FAILED' THEN 'FAILED'
                        WHEN NOT EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('RECEIVED','PARSED','NORMALIZED'))
                         AND (EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('INVALID','BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','MATCH_CANDIDATES')) OR EXISTS (SELECT 1 FROM exercise_import.import_issue i WHERE i.batch_id=? AND i.severity IN ('ERROR','BLOCKER') AND i.resolved_at IS NULL)) THEN 'COMPLETED_WITH_ISSUES'
                        WHEN NOT EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('RECEIVED','PARSED','NORMALIZED')) THEN 'COMPLETED'
                        ELSE 'PROCESSING' END,
                    completed_at=CASE WHEN EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? ) AND NOT EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('RECEIVED','PARSED','NORMALIZED')) THEN COALESCE(batch.completed_at,?) ELSE NULL END,
                    version=batch.version+1 WHERE batch.id=?
                """, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, Timestamp.from(clock.instant()), batchId);
    }

    private boolean readyForDraft(UUID recordId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT status='READY_FOR_DRAFT' AND draft_version_id IS NULL FROM exercise_import.import_record WHERE id=?",
                Boolean.class, recordId));
    }

    private void draftIssue(UUID batchId, UUID recordId) {
        if (batchId == null) return;
        jdbc.update("""
                INSERT INTO exercise_import.import_issue(id,batch_id,record_id,row_number,code,stage,severity,json_pointer,message,created_at)
                SELECT ?,?,?,row_number,'DRAFT_CREATION_FAILED','CREATE_DRAFT','ERROR','/',
                       'Nie można było utworzyć szkicu. Rekord pozostaje gotowy do bezpiecznego ponowienia.',?
                  FROM exercise_import.import_record record
                 WHERE record.id=?
                   AND NOT EXISTS (SELECT 1 FROM exercise_import.import_issue issue
                                   WHERE issue.record_id=record.id AND issue.code='DRAFT_CREATION_FAILED'
                                     AND issue.resolved_at IS NULL)
                """, UUID.randomUUID(), batchId, recordId, Timestamp.from(clock.instant()), recordId);
    }
    private static void page(int p,int s){if(p<0||s<1||s>100)throw badRequest("page must be >= 0 and size between 1 and 100");}
    private static String safeFilename(String value){String name=value==null?"import.jsonl":value.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);return name.isBlank()?"import.jsonl":name.substring(0,Math.min(255,name.length()));}
    private static String text(String value,int max,String field){String v=value==null?"":value.trim();if(v.isBlank()||v.length()>max)throw badRequest(field+" is required and limited to "+max+" characters");return v;}
    private static String code(String value,int max){String v=text(value,max,"code").toUpperCase(Locale.ROOT);if(!v.matches("[A-Z0-9_:-]+"))throw badRequest("code contains unsupported characters");return v;}
    private static String normalizeLocale(String value){Locale l=Locale.forLanguageTag(text(value,35,"locale").replace('_','-'));if(l.getLanguage().isBlank())throw badRequest("invalid locale");return l.toLanguageTag();}
    private static String blankToNull(String v){return v==null||v.isBlank()?null:v.toUpperCase(Locale.ROOT);}
    private static String csv(String v){return "\""+(v==null?"":v.replace("\"","\"\""))+"\"";}
    private static ResponseStatusException badRequest(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private static ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}
    private static IssueView issueView(java.sql.ResultSet rs)throws java.sql.SQLException{return new IssueView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),(Long)rs.getObject(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),instant(rs,10),instant(rs,11));}
    private static BatchView batchView(java.sql.ResultSet rs)throws java.sql.SQLException{return new BatchView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getObject(5,UUID.class),rs.getString(6),instant(rs,7),instant(rs,8),instant(rs,9),rs.getInt(10),rs.getInt(11),rs.getInt(12),rs.getInt(13),rs.getInt(14),rs.getInt(15),rs.getLong(16),new ArtifactView(rs.getString(17),rs.getString(18),rs.getLong(19),rs.getString(20)));}
    private static Instant instant(java.sql.ResultSet rs,int column)throws java.sql.SQLException{OffsetDateTime value=rs.getObject(column,OffsetDateTime.class);return value==null?null:value.toInstant();}

    record CreateSource(String code,String displayName,String defaultLocale,String licenseCode,boolean licenseVerified){}
    record SourceView(UUID id,String code,String displayName,String defaultLocale,String licenseCode,boolean licenseVerified,boolean active,Instant createdAt,long version){}
    record ArtifactView(String originalFilename,String mediaType,long byteSize,String sha256){}
    record BatchView(UUID id,UUID sourceId,String requestKey,String status,UUID forcedFromBatchId,String submittedBySubject,Instant submittedAt,Instant startedAt,Instant completedAt,int totalCount,int validCount,int invalidCount,int blockedCount,int draftedCount,int unchangedCount,long version,ArtifactView artifact){}
    record RecordSummary(UUID id,long rowNumber,String sourceRecordKey,String status,String rawSha256,String normalizedSha256,UUID matchedExerciseId,UUID draftVersionId,long version){}
    record RecordPage(List<RecordSummary> content,int page,int size,long totalElements){}
    record IssueView(UUID id,UUID recordId,Long rowNumber,String sourceRecordKey,String code,String stage,String severity,String jsonPointer,String message,Instant createdAt,Instant resolvedAt){}

    record CandidateView(UUID id, UUID exerciseId, String exerciseName, int rank, java.math.BigDecimal score,
                         JsonNode reasons, String algorithmVersion, String decision, String decidedBySubject,
                         Instant decidedAt, long version) {
    }
    record RecordDetail(UUID id,UUID batchId,long rowNumber,String sourceRecordKey,String status,JsonNode raw,JsonNode normalized,String rawSha256,String normalizedSha256,String normalizationVersion,UUID matchedExerciseId,UUID draftVersionId,Instant createdAt,Instant updatedAt,long version,List<IssueView> issues,List<CandidateView> matchCandidates){}
    record MatchDecision(UUID candidateId,String decision){}
    record MappingDecision(String decision,String canonicalValue){}
    record MappingView(UUID id,UUID sourceId,String dictionaryType,String sourceValue,String canonicalValue,String status,String decidedBySubject,Instant decidedAt,long version){}
}
