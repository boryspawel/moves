package com.motionecosystem.exercisecatalog;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.audit.api.TransactionalOutbox;
import com.motionecosystem.exercisecatalog.api.PublishExerciseVersion;
import com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExerciseEditorialWorkflowService implements ReviewExerciseVersion, PublishExerciseVersion {
    private static final Set<String> AREAS = Set.of("CONTENT","TECHNIQUE","ANATOMY_EXPOSURE","LICENSE","MEDIA");
    private static final Set<String> DECISIONS = Set.of("APPROVED","CHANGES_REQUESTED");
    private final JdbcTemplate jdbc;
    private final TransactionalOutbox outbox;
    private final AuditRecorder audit;
    private final Clock clock;

    @Override
    @Transactional
    public ReviewResult review(UUID versionId, String actor, ReviewCommand command) {
        if (command == null) throw badRequest("review is required");
        String area = normalized(command.area(), AREAS, "review area");
        String decision = normalized(command.decision(), DECISIONS, "review decision");
        VersionState version = locked(versionId);
        if (!Set.of("DRAFT","IN_REVIEW","CHANGES_REQUESTED","APPROVED").contains(version.status))
            throw conflict("published or withdrawn versions cannot be reviewed");
        if (command.expectedVersion() != null && command.expectedVersion() != version.version)
            throw conflict("exercise version was changed concurrently");
        Instant now = clock.instant();
        if (version.status.equals("DRAFT") || version.status.equals("CHANGES_REQUESTED") || version.status.equals("APPROVED")) {
            int changed = jdbc.update("""
                    UPDATE exercise_catalog.exercise_version
                       SET status='IN_REVIEW',reviewed_by_subject=NULL,reviewed_at=NULL,version=version+1
                     WHERE id=? AND version=?
                    """, versionId, version.version);
            if (changed != 1) throw conflict("exercise version was changed concurrently");
        }
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_review(
                    id,exercise_version_id,review_area,decision,comment,reviewer_subject,reviewed_at,version)
                VALUES (?,?,?,?,?,?,?,0)
                ON CONFLICT(exercise_version_id,review_area,reviewer_subject) DO UPDATE SET
                    decision=excluded.decision,comment=excluded.comment,reviewed_at=excluded.reviewed_at,
                    version=exercise_catalog.exercise_review.version+1
                """, UUID.randomUUID(),versionId,area,decision,trim(command.comment(),4000),actor,Timestamp.from(now));
        audit.record(actor,"EXERCISE_VERSION_REVIEW_RECORDED","ExerciseVersion",versionId);
        if (decision.equals("CHANGES_REQUESTED")) {
            jdbc.update("UPDATE exercise_catalog.exercise_version SET status='CHANGES_REQUESTED',version=version+1 WHERE id=?",versionId);
        } else {
            List<String> unmet = unmet(versionId);
            if (unmet.isEmpty()) jdbc.update("""
                    UPDATE exercise_catalog.exercise_version SET status='APPROVED',reviewed_by_subject=?,reviewed_at=?,version=version+1
                     WHERE id=? AND status='IN_REVIEW'
                    """,actor,Timestamp.from(now),versionId);
        }
        VersionState current = state(versionId);
        return new ReviewResult(versionId,current.status,current.version,reviews(versionId),unmet(versionId));
    }

    @Override
    @Transactional
    public PublicationResult publish(UUID versionId, String actor, Long expectedVersion) {
        VersionState version=locked(versionId);
        if(expectedVersion!=null&&expectedVersion!=version.version)throw conflict("exercise version was changed concurrently");
        List<String> unmet=unmet(versionId);
        if(!version.status.equals("APPROVED"))unmet=append(unmet,"STATUS_APPROVED_REQUIRED");
        if(!unmet.isEmpty())throw conflict("publication requirements not met: "+String.join(",",unmet));
        Instant now=clock.instant();
        int changed=jdbc.update("UPDATE exercise_catalog.exercise_version SET status='PUBLISHED',published_at=?,version=version+1 WHERE id=? AND version=? AND status='APPROVED'",
                Timestamp.from(now),versionId,version.version);
        if(changed!=1)throw conflict("exercise version was changed concurrently");
        outbox.append("ExerciseVersion",versionId,"ExerciseVersionPublished",
                "{\"exerciseVersionId\":\""+versionId+"\",\"publishedBy\":\""+json(actor)+"\"}",now);
        audit.record(actor,"EXERCISE_VERSION_PUBLISHED","ExerciseVersion",versionId);
        audit.record(actor,"CAPABILITY_PUBLISH_EXERCISE_CONTENT","ExerciseVersion",versionId);
        VersionState published=state(versionId);
        return new PublicationResult(versionId,published.status,now,published.version,List.of());
    }

    @Transactional(readOnly=true)
    public ReviewResult status(UUID versionId){VersionState value=state(versionId);return new ReviewResult(versionId,value.status,value.version,reviews(versionId),unmet(versionId));}

    @Transactional(readOnly=true)
    public VersionDiff diff(UUID versionId){
        VersionDiff value=jdbc.query("""
                SELECT version.id,version.exercise_id,version.version_number,version.status,version.semantic_sha256,
                       record.normalized_payload::text,
                       (SELECT older.semantic_sha256 FROM exercise_catalog.exercise_version older
                         WHERE older.exercise_id=version.exercise_id AND older.status='PUBLISHED'
                         ORDER BY older.version_number DESC LIMIT 1)
                  FROM exercise_catalog.exercise_version version
                  LEFT JOIN exercise_import.import_record record ON record.id=version.import_record_id
                 WHERE version.id=?
                """,rs->rs.next()?new VersionDiff(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)):null,versionId);
        if(value==null)throw notFound();return value;
    }

    private List<String> unmet(UUID versionId){
        List<String> result=new ArrayList<>();
        for(String area:List.of("CONTENT","TECHNIQUE","ANATOMY_EXPOSURE","LICENSE"))if(!latestApproved(versionId,area))result.add("REVIEW_"+area+"_REQUIRED");
        if(hasMedia(versionId)&&!latestApproved(versionId,"MEDIA"))result.add("REVIEW_MEDIA_REQUIRED");
        if(count(versionId,"exercise_catalog.exercise_load_characteristic")==0)result.add("LOAD_CHARACTERISTIC_REQUIRED");
        if(count(versionId,"exercise_catalog.exercise_contribution")==0)result.add("ANATOMY_EXPOSURE_REQUIRED");
        if(count(versionId,"exercise_catalog.evidence_source")==0)result.add("EVIDENCE_REQUIRED");
        if(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM exercise_import.import_record record
                JOIN exercise_import.import_issue issue ON issue.record_id=record.id
                WHERE record.draft_version_id=? AND issue.resolved_at IS NULL AND issue.severity IN ('ERROR','BLOCKER'))
                """,Boolean.class,versionId)))result.add("UNRESOLVED_IMPORT_ISSUES");
        boolean therapeutic=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM exercise_catalog.exercise_version_purpose WHERE exercise_version_id=? AND purpose='THERAPEUTIC_EXERCISE')",Boolean.class,versionId));
        if(therapeutic){Integer reviewers=jdbc.queryForObject("SELECT COUNT(DISTINCT reviewer_subject) FROM exercise_catalog.exercise_review WHERE exercise_version_id=? AND decision='APPROVED'",Integer.class,versionId);if(reviewers==null||reviewers<2)result.add("TWO_INDEPENDENT_REVIEWERS_REQUIRED");}
        return List.copyOf(result);
    }

    private boolean latestApproved(UUID id,String area){return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT COALESCE((SELECT decision='APPROVED' FROM exercise_catalog.exercise_review
            WHERE exercise_version_id=? AND review_area=? ORDER BY reviewed_at DESC,id DESC LIMIT 1),FALSE)
            """,Boolean.class,id,area));}
    private boolean hasMedia(UUID id){return count(id,"exercise_catalog.exercise_media")>0;}
    private int count(UUID id,String table){Integer value=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE exercise_version_id=?",Integer.class,id);return value==null?0:value;}
    private List<ReviewItem> reviews(UUID id){return jdbc.query("SELECT id,review_area,decision,comment,reviewer_subject,reviewed_at,version FROM exercise_catalog.exercise_review WHERE exercise_version_id=? ORDER BY reviewed_at,id",(rs,n)->new ReviewItem(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getObject(6,OffsetDateTime.class).toInstant(),rs.getLong(7)),id);}
    private VersionState locked(UUID id){VersionState state=jdbc.query("SELECT status,version FROM exercise_catalog.exercise_version WHERE id=? FOR UPDATE",rs->rs.next()?new VersionState(rs.getString(1),rs.getLong(2)):null,id);if(state==null)throw notFound();return state;}
    private VersionState state(UUID id){VersionState state=jdbc.query("SELECT status,version FROM exercise_catalog.exercise_version WHERE id=?",rs->rs.next()?new VersionState(rs.getString(1),rs.getLong(2)):null,id);if(state==null)throw notFound();return state;}
    private static String normalized(String value,Set<String> allowed,String field){String result=value==null?"":value.trim().toUpperCase(Locale.ROOT);if(!allowed.contains(result))throw badRequest("invalid "+field);return result;}
    private static String trim(String value,int max){if(value==null||value.isBlank())return null;String result=value.trim();if(result.length()>max)throw badRequest("comment is too long");return result;}
    private static List<String> append(List<String> list,String value){List<String> result=new ArrayList<>(list);result.add(value);return List.copyOf(result);}
    private static String json(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
    private static ResponseStatusException badRequest(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}private static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"exercise version not found");}
    private record VersionState(String status,long version){}
    public record VersionDiff(UUID versionId,UUID exerciseId,int versionNumber,String status,String draftSemanticSha256,String normalizedSource,String currentPublishedSemanticSha256){}
}
