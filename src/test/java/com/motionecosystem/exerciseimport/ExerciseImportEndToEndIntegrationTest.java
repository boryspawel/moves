package com.motionecosystem.exerciseimport;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes=MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class ExerciseImportEndToEndIntegrationTest {
    private static final Path ARTIFACTS=tempDirectory();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("exercise-import.artifacts.directory",()->ARTIFACTS.toString());}
    @Autowired WebApplicationContext context; @Autowired FilterChainProxy filters;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ExerciseImportService imports;
    MockMvc mvc;
    @MockitoSpyBean ImportRecordUseCases useCases;

    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        jdbc.update("""
                INSERT INTO anatomy_reference.anatomical_structure(
                    id,code,type,display_name,side_policy,status,taxonomy_version,created_by_subject,
                    created_at,published_at,version) VALUES (?, 'TEST_WHOLE_BODY','BODY_REGION','Test whole body',
                    'NONE','PUBLISHED',1,'test',now(),now(),0) ON CONFLICT(code) DO NOTHING
                """,UUID.fromString("00000000-0000-0000-0000-000000000777"));}
    @AfterEach void clean(){jdbc.execute("""
            TRUNCATE TABLE exercise_import.import_source,exercise_catalog.exercise,
            anatomy_reference.anatomical_structure,audit.audit_event,audit.outbox_event,
            batch_job_instance CASCADE
            """);}

    @Test void jsonlToDraftReviewPublishAndForcedReimportIsUnchanged()throws Exception{
        UUID source=createSource("FIXTURE",true);byte[] file=resource("fixtures/exercise-import-valid.jsonl");
        UUID batch=upload(source,"request-1",false,file,contentAdmin("editor-one"));await(batch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='DRAFTED'", Integer.class, batch)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_match_candidate candidate JOIN exercise_import.import_record record ON record.id=candidate.record_id WHERE record.batch_id=?", Integer.class, batch)).isZero();
        UUID version = jdbc.queryForObject("SELECT draft_version_id FROM exercise_import.import_record WHERE batch_id=? ORDER BY row_number LIMIT 1", UUID.class, batch);
        long optimistic=jdbc.queryForObject("SELECT version FROM exercise_catalog.exercise_version WHERE id=?",Long.class,version);
        for(String area:new String[]{"CONTENT","TECHNIQUE","ANATOMY_EXPOSURE","LICENSE"}){
            String response=mvc.perform(post("/api/v1/admin/exercise-versions/{id}/reviews",version).with(contentAdmin("editor-one"))
                    .contentType("application/json").content("{\"area\":\""+area+"\",\"decision\":\"APPROVED\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            optimistic=json.readTree(response).path("version").asLong();
        }
        mvc.perform(post("/api/v1/admin/exercise-versions/{id}/publish",version).with(contentAdmin("publisher"))
                .contentType("application/json").content("{\"expectedVersion\":"+optimistic+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit.outbox_event WHERE aggregate_id=? AND event_type='ExerciseVersionPublished'",Integer.class,version)).isOne();
        assertThatThrownBy(()->jdbc.update("UPDATE exercise_catalog.exercise_instruction_step SET instruction='mutated' WHERE exercise_version_id=?",version))
                .hasMessageContaining("immutable");

        assertThat(upload(source,"request-1",false,file,contentAdmin("editor-one"))).isEqualTo(batch);
        assertThat(upload(source,"request-2",false,file,contentAdmin("editor-one"))).isEqualTo(batch);
        UUID forced=upload(source,"request-3",true,file,systemAdmin("admin"));await(forced);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='UNCHANGED'",Integer.class,forced)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT forced_from_batch_id FROM exercise_import.import_batch WHERE id=?",UUID.class,forced)).isEqualTo(batch);
        assertThat(Files.list(ARTIFACTS).count()).isGreaterThanOrEqualTo(2);

        byte[] changed = new String(file).replace("Fikcyjny przysiad testowy", "Fikcyjny przysiad zmieniony").getBytes();
        UUID changedBatch = upload(source, "request-4", false, changed, contentAdmin("editor-one"));
        await(changedBatch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='DRAFTED'", Integer.class, changedBatch)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_match_candidate candidate JOIN exercise_import.import_record record ON record.id=candidate.record_id WHERE record.batch_id=?", Integer.class, changedBatch)).isZero();
    }

    @Test
    void draftCreationFailureIsVisibleAndCanBeRetried() throws Exception {
        doThrow(new IllegalStateException("controlled draft failure")).doCallRealMethod()
                .when(useCases).createDraft(any(UUID.class), any(String.class));
        UUID source = createSource("DRAFT_RETRY", true);
        UUID batch = upload(source, "draft-retry-1", false, resource("fixtures/exercise-import-valid.jsonl"), contentAdmin("editor"));
        await(batch);
        assertThat(jdbc.queryForObject("SELECT status FROM exercise_import.import_batch WHERE id=?", String.class, batch)).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='READY_FOR_DRAFT'", Integer.class, batch)).isOne();
        assertThat(jdbc.queryForObject("SELECT blocked_count FROM exercise_import.import_batch WHERE id=?", Integer.class, batch)).isOne();
        UUID record = jdbc.queryForObject("SELECT record_id FROM exercise_import.import_issue WHERE batch_id=? AND code='DRAFT_CREATION_FAILED'", UUID.class, batch);
        imports.createDraft("editor", record);
        assertThat(jdbc.queryForObject("SELECT status FROM exercise_import.import_record WHERE id=?", String.class, record)).isEqualTo("DRAFTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_issue WHERE record_id=? AND code='DRAFT_CREATION_FAILED' AND resolved_at IS NULL", Integer.class, record)).isZero();
    }

    @Test
    void resolvedMatchCreatesDraftImmediatelyAndFailureIsRetryableWithoutDuplicateIssue() throws Exception {
        UUID source = createSource("MATCH_DECISION", true);
        UUID batch = upload(source, "match-decision-1", false, resource("fixtures/exercise-import-valid.jsonl"), contentAdmin("editor"));
        await(batch);
        UUID imported = jdbc.queryForObject("SELECT id FROM exercise_import.import_record WHERE batch_id=? ORDER BY row_number LIMIT 1", UUID.class, batch);
        UUID exercise = jdbc.queryForObject("SELECT matched_exercise_id FROM exercise_import.import_record WHERE id=?", UUID.class, imported);
        UUID record = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_import.import_record(
                    id,batch_id,row_number,source_record_key,status,raw_payload,normalized_payload,raw_sha256,
                    normalized_sha256,normalization_version,created_at,updated_at,version)
                SELECT ?,batch_id,99,'attention-record','MATCH_CANDIDATES',raw_payload,normalized_payload,raw_sha256,
                       ?,normalization_version,now(),now(),0
                  FROM exercise_import.import_record WHERE id=?
                """, record, "a".repeat(64), imported);
        jdbc.update("""
                INSERT INTO exercise_import.import_match_candidate(id,record_id,exercise_id,rank,score,reasons,algorithm_version,version)
                VALUES (?,?,?,1,1.0,CAST('[\"test candidate\"]' AS jsonb),'test',0)
                """, candidate, record, exercise);
        doThrow(new IllegalStateException("controlled decision draft failure")).doCallRealMethod()
                .when(useCases).createDraft(any(UUID.class), any(String.class));

        assertThat(imports.decideMatch("editor", record, new ExerciseImportService.MatchDecision(candidate, "SAME")).status()).isEqualTo("READY_FOR_DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_issue WHERE record_id=? AND code='DRAFT_CREATION_FAILED' AND resolved_at IS NULL", Integer.class, record)).isOne();

        assertThat(imports.decideMatch("editor", record, new ExerciseImportService.MatchDecision(candidate, "SAME")).status()).isEqualTo("DRAFTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_issue WHERE record_id=? AND code='DRAFT_CREATION_FAILED'", Integer.class, record)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_issue WHERE record_id=? AND code='DRAFT_CREATION_FAILED' AND resolved_at IS NULL", Integer.class, record)).isZero();
        UUID draft = jdbc.queryForObject("SELECT draft_version_id FROM exercise_import.import_record WHERE id=?", UUID.class, record);
        imports.decideMatch("editor", record, new ExerciseImportService.MatchDecision(candidate, "SAME"));
        assertThat(jdbc.queryForObject("SELECT draft_version_id FROM exercise_import.import_record WHERE id=?", UUID.class, record)).isEqualTo(draft);
    }

    @Test void partialFileKeepsValidRecordAndReportsMalformedAndUnsupportedLines()throws Exception{
        UUID source=createSource("PARTIAL",true);UUID batch=upload(source,"partial-1",false,resource("fixtures/exercise-import-partial.jsonl"),contentAdmin("editor"));await(batch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=?",Integer.class,batch)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='DRAFTED'", Integer.class, batch)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_issue WHERE batch_id=? AND code IN ('MALFORMED_JSON','UNSUPPORTED_SCHEMA_VERSION')",Integer.class,batch)).isEqualTo(2);
        mvc.perform(get("/api/v1/admin/exercise-import/batches/{id}/records",batch).param("size","1").with(contentAdmin("editor")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/v1/admin/exercise-import/batches/{id}/issues",batch)
                        .param("format","jsonl").with(contentAdmin("editor")))
                .andExpect(status().isOk()).andExpect(result -> assertThat(
                        result.getResponse().getContentAsString()).contains("MALFORMED_JSON","UNSUPPORTED_SCHEMA_VERSION"));
        mvc.perform(get("/api/v1/admin/exercise-import/batches/{id}",batch)).andExpect(status().isUnauthorized());
    }

    @Test void mappingLicenseAndTherapeuticIndependentReviewRulesBlockUnsafeProgress()throws Exception{
        UUID unlicensed=createSource("NO_LICENSE",false);
        UUID licenseBatch=upload(unlicensed,"license-1",false,resource("fixtures/exercise-import-valid.jsonl"),contentAdmin("editor"));await(licenseBatch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='BLOCKED_BY_LICENSE'",Integer.class,licenseBatch)).isEqualTo(2);

        UUID source=createSource("MAPPING",true);
        String unknown=new String(resource("fixtures/exercise-import-valid.jsonl")).replace("BODYWEIGHT\"]","UNKNOWN_DEVICE\"]");
        UUID mappingBatch=upload(source,"mapping-1",false,unknown.getBytes(),contentAdmin("editor"));await(mappingBatch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='BLOCKED_BY_MAPPING'",Integer.class,mappingBatch)).isGreaterThan(0);
        UUID mapping=jdbc.queryForObject("SELECT id FROM exercise_import.import_mapping WHERE source_id=? AND source_value='UNKNOWN_DEVICE'",UUID.class,source);
        mvc.perform(post("/api/v1/admin/exercise-import/mappings/{id}/decision",mapping).with(contentAdmin("editor"))
                .contentType("application/json").content("{\"decision\":\"APPROVED\",\"canonicalValue\":\"BODYWEIGHT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='BLOCKED_BY_MAPPING'",Integer.class,mappingBatch)).isZero();
    }

    @Test void failedChunkRestartsWithoutDuplicatingRecords()throws Exception{
        TransientDataAccessResourceException controlled=new TransientDataAccessResourceException("controlled retryable failure");
        doThrow(controlled).doThrow(controlled).doThrow(controlled).doCallRealMethod()
                .when(useCases).validate(any(UUID.class));
        UUID source=createSource("RESTART",true);
        UUID batch=upload(source,"restart-1",false,resource("fixtures/exercise-import-valid.jsonl"),contentAdmin("editor"));
        awaitStatus(batch,"FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=?",Integer.class,batch)).isEqualTo(2);

        mvc.perform(post("/api/v1/admin/exercise-import/batches/{id}/restart",batch).with(contentAdmin("editor")))
                .andExpect(status().isAccepted());
        await(batch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=?",Integer.class,batch)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=? AND status='DRAFTED'", Integer.class, batch)).isEqualTo(2);
    }

    private UUID createSource(String code,boolean verified)throws Exception{String body=mvc.perform(post("/api/v1/admin/exercise-import/sources").with(contentAdmin("editor"))
            .contentType("application/json").content("{\"code\":\""+code+"\",\"displayName\":\"Fixture "+code+"\",\"defaultLocale\":\"pl-PL\",\"licenseCode\":\"CC0-1.0\",\"licenseVerified\":"+verified+"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return UUID.fromString(json.readTree(body).path("id").asText());}
    private UUID upload(UUID source,String request,boolean force,byte[] bytes,RequestPostProcessor auth)throws Exception{MockMultipartFile file=new MockMultipartFile("file","fixture.jsonl","application/x-ndjson",bytes);MockMultipartHttpServletRequestBuilder builder=multipart("/api/v1/admin/exercise-import/batches").file(file).param("sourceId",source.toString()).param("forceReprocess",String.valueOf(force)).header("Idempotency-Key",request);String body=mvc.perform(builder.with(auth)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();return UUID.fromString(json.readTree(body).path("batchId").asText());}
    private void await(UUID batch)throws Exception{Instant end=Instant.now().plus(Duration.ofSeconds(15));while(Instant.now().isBefore(end)){String status=jdbc.queryForObject("SELECT status FROM exercise_import.import_batch WHERE id=?",String.class,batch);if(!status.matches("RECEIVED|QUEUED|PROCESSING")){assertThat(status).doesNotStartWith("FAILED");return;}Thread.sleep(100);}throw new AssertionError("batch did not complete");}
    private void awaitStatus(UUID batch,String expected)throws Exception{Instant end=Instant.now().plus(Duration.ofSeconds(15));while(Instant.now().isBefore(end)){String status=jdbc.queryForObject("SELECT status FROM exercise_import.import_batch WHERE id=?",String.class,batch);if(status.equals(expected))return;Thread.sleep(100);}throw new AssertionError("batch did not reach "+expected);}
    private static byte[] resource(String name)throws Exception{return new ClassPathResource(name).getInputStream().readAllBytes();}
    private static JwtRequestPostProcessor contentAdmin(String subject){return jwt().jwt(token->token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN"));}
    private static JwtRequestPostProcessor systemAdmin(String subject){return jwt().jwt(token->token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN"),new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));}
    private static Path tempDirectory(){try{return Files.createTempDirectory("moves-exercise-import-test-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
}
