package com.motionecosystem.exercisecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.identityaccess.api.EditorialCapability;
import com.motionecosystem.safety.domain.SafetyRules;
import com.motionecosystem.support.PostgresTestConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class CatalogAndSafetyIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy securityFilterChain;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired ExerciseCatalogQueryPort catalogPort;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.audit_event,
                    safety.readiness_check_in,
                    safety.participant_restriction,
                    exercise_catalog.exercise,
                    anatomy_reference.anatomical_structure,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void reviewedProfileRejectsInvalidIntervalsAndParentChildAllocationButAllowsDescriptiveAndVariants()
            throws Exception {
        UUID parent = createAnatomy("LOWER_LIMB", "BODY_REGION");
        UUID child = createAnatomy("KNEE_JOINT", "JOINT");
        addAnatomyRelation(parent, child);
        publishAnatomy(parent);
        publishAnatomy(child);
        UUID versionId = createExercise("Supported squat").versionId();

        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/publish", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isConflict());
        addLoadCharacteristics(versionId);
        UUID evidenceId = addEvidence(versionId);

        addContribution(versionId, parent, evidenceId, "ALLOCATION", "STANDARD", "LEFT", "1.100000")
                .andExpect(status().isBadRequest());
        addContribution(versionId, parent, evidenceId, "ALLOCATION", "STANDARD", "LEFT", "0.600000")
                .andExpect(status().isOk());
        addContribution(versionId, child, evidenceId, "ALLOCATION", "STANDARD", "LEFT", "0.300000")
                .andExpect(status().isConflict());
        addContribution(versionId, child, evidenceId, "DESCRIPTIVE_ONLY", "STANDARD", "LEFT", "0.300000")
                .andExpect(status().isOk());
        addContribution(versionId, child, evidenceId, "ALLOCATION", "ASSISTED", "LEFT", "0.300000")
                .andExpect(status().isOk());
        addContribution(versionId, child, evidenceId, "ALLOCATION", "STANDARD", "RIGHT", "0.300000")
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/publish", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/submit-review", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        mvc.perform(put("/api/v1/admin/exercises/versions/{id}", versionId)
                        .with(contentAdmin()).contentType("application/json").content(versionCommand()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/request-changes", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"));
        mvc.perform(put("/api/v1/admin/exercises/versions/{id}", versionId)
                        .with(contentAdmin()).contentType("application/json").content(versionCommand()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/submit-review", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/approve", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/publish", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit.audit_event
                WHERE event_type = 'CAPABILITY_PUBLISH_EXERCISE_CONTENT'
                  AND aggregate_id = ?
                """, Long.class, versionId)).isOne();
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/evidence", versionId)
                        .with(contentAdmin()).contentType("application/json").content(evidenceRequest()))
                .andExpect(status().isConflict());

        ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot snapshot = catalogPort
                .findPublishedVersion(versionId).orElseThrow();
        assertThat(snapshot.getClass().isAnnotationPresent(jakarta.persistence.Entity.class)).isFalse();
        assertThat(snapshot.movementPatterns()).hasSize(2);
        assertThat(snapshot.contributions()).hasSize(4)
                .allSatisfy(item -> assertThat(item.evidence()).isNotEmpty());
    }

    @Test
    void catalogUsesPagedCurrentVersionProjectionWithoutDuplicatesOrNPlusOne() throws Exception {
        UUID structureId = createPublishedAnatomy("QUADRICEPS", "MUSCLE_GROUP");
        CreatedExercise alphaV1 = createExercise("Alpha squat");
        completeAndPublish(alphaV1.versionId(), structureId);
        UUID alphaV2 = createNextVersion(alphaV1.exerciseId());
        completeAndPublish(alphaV2, structureId);
        CreatedExercise beta = createExercise("Beta squat");
        completeAndPublish(beta.versionId(), structureId);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        mvc.perform(get("/api/v1/exercises").with(participant("participant"))
                        .param("movementPattern", "SQUAT").param("equipment", "band")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].versionId").value(alphaV2.toString()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);

        mvc.perform(get("/api/v1/exercises").with(participant("participant"))
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].canonicalName").value("Beta squat"));
    }

    @Test
    void concurrentNextVersionsAreSerializedWithoutRawServerError() throws Exception {
        CreatedExercise base = createExercise("Concurrent exercise");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> createVersionConcurrently(base.exerciseId(), ready, start));
            Future<Integer> second = executor.submit(() -> createVersionConcurrently(base.exerciseId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactly(200, 200);
        }
        assertThat(jdbc.queryForList("""
                SELECT version_number FROM exercise_catalog.exercise_version
                WHERE exercise_id = ? ORDER BY version_number
                """, Integer.class, base.exerciseId())).containsExactly(1, 2, 3);
    }

    @Test
    void legacyContraindicationTagsAreReadOnlyReportedAndAbsentFromNewCatalogContract() throws Exception {
        UUID structureId = createPublishedAnatomy("HAMSTRINGS", "MUSCLE_GROUP");
        UUID versionId = createExercise("Legacy-safe hinge").versionId();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM exercise_catalog.exercise_version_contraindication", Long.class)).isZero();
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version_contraindication
                    (exercise_version_id, contraindication_tag) VALUES (?, 'ACUTE_KNEE_PAIN')
                """, versionId);
        completeAndPublish(versionId, structureId);

        mvc.perform(get("/api/v1/exercises").with(participant("participant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contraindicationTags").doesNotExist());
        mvc.perform(get("/api/v1/admin/exercises/legacy/contraindications").with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tag").value("ACUTE_KNEE_PAIN"))
                .andExpect(jsonPath("$[0].disposition").value("UNMAPPED_READ_ONLY"));
    }

    @Test
    void contentAdministrationIsProtectedAndOpenApiSeparatesListFromEditorialProfile() throws Exception {
        assertThat(EditorialCapability.values()).containsExactly(
                EditorialCapability.PUBLISH_EXERCISE_CONTENT,
                EditorialCapability.PUBLISH_SAFETY_RULE);
        assertThat(SafetyRules.PUBLICATION_CAPABILITY).isEqualTo("PUBLISH_SAFETY_RULE");
        mvc.perform(post("/api/v1/admin/exercises").with(participant("participant"))
                        .contentType("application/json").content(createExerciseRequest("Forbidden")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/exercises']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/exercises/versions/{versionId}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/exercises/versions/{versionId}/contributions']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/safety/me/restrictions']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v2/safety/me/restrictions']").exists())
                .andExpect(jsonPath("$.paths['/api/v2/safety/admin/legacy/participant-restrictions']")
                        .exists());
    }

    @Test
    void safetyInputsRemainNonDiagnosticAndLegacyReplaceAllIsRemoved() throws Exception {
        mvc.perform(get("/api/v1/safety/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/safety/me/restrictions").with(participant("first"))
                        .contentType("application/json")
                        .content("{\"contraindicationTags\":[\"acute_knee_pain\"]}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/safety/me/check-ins").with(participant("first"))
                        .contentType("application/json")
                        .content("{\"painLevel\":7,\"readinessLevel\":2,\"painArea\":\"knee\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value(
                        "User-reported input for specialist review; this is not a diagnosis."));
        mvc.perform(get("/api/v1/safety/me").with(participant("second")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contraindicationTags.length()").value(0));
    }

    @Test
    void publicDetailContainsPublishedEditorialDataWithoutTechnicalRecordIds() throws Exception {
        UUID structureId = createPublishedAnatomy("QUADRICEPS", "MUSCLE_GROUP");
        CreatedExercise exercise = createExercise("Public squat");
        completeAndPublish(exercise.versionId(), structureId);

        mvc.perform(get("/api/v1/exercises/versions/{id}", exercise.versionId()).with(participant("participant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalName").value("Public squat"))
                .andExpect(jsonPath("$.instruction").exists())
                .andExpect(jsonPath("$.anatomyContributions[0].code").value("QUADRICEPS"))
                .andExpect(jsonPath("$.anatomyContributions[0].displayName").value("QUADRICEPS"))
                .andExpect(jsonPath("$.anatomyContributions[0].structureType").value("MUSCLE_GROUP"))
                .andExpect(jsonPath("$.anatomyContributions[0].coefficientLow").value(0.2))
                .andExpect(jsonPath("$.loadCharacteristics[0].id").doesNotExist())
                .andExpect(jsonPath("$.evidence[0].id").doesNotExist())
                .andExpect(jsonPath("$.mediaReference").doesNotExist())
                .andExpect(jsonPath("$.legacyContraindicationTags").doesNotExist());

        CreatedExercise draft = createExercise("Draft squat");
        mvc.perform(get("/api/v1/exercises/versions/{id}", draft.versionId()).with(participant("participant")))
                .andExpect(status().isNotFound());
    }

    private CreatedExercise createExercise(String name) throws Exception {
        mvc.perform(post("/api/v1/admin/exercises").with(contentAdmin())
                        .contentType("application/json").content(createExerciseRequest(name)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        return jdbc.queryForObject("""
                SELECT exercise.id, version.id
                FROM exercise_catalog.exercise exercise
                JOIN exercise_catalog.exercise_version version ON version.exercise_id = exercise.id
                WHERE exercise.canonical_name = ? AND version.version_number = 1
                """, (result, row) -> new CreatedExercise(
                result.getObject(1, UUID.class), result.getObject(2, UUID.class)), name);
    }

    private UUID createNextVersion(UUID exerciseId) throws Exception {
        mvc.perform(post("/api/v1/admin/exercises/{id}/versions", exerciseId).with(contentAdmin())
                        .contentType("application/json").content(versionCommand()))
                .andExpect(status().isOk());
        return jdbc.queryForObject("""
                SELECT id FROM exercise_catalog.exercise_version
                WHERE exercise_id = ? ORDER BY version_number DESC LIMIT 1
                """, UUID.class, exerciseId);
    }

    private void completeAndPublish(UUID versionId, UUID structureId) throws Exception {
        addLoadCharacteristics(versionId);
        UUID evidenceId = addEvidence(versionId);
        addContribution(versionId, structureId, evidenceId, "ALLOCATION", "STANDARD",
                "AS_PRESCRIBED", "0.700000").andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/submit-review", versionId)
                        .with(contentAdmin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/approve", versionId)
                        .with(contentAdmin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/publish", versionId)
                        .with(contentAdmin())).andExpect(status().isOk());
    }

    private void addLoadCharacteristics(UUID versionId) throws Exception {
        mvc.perform(put("/api/v1/admin/exercises/versions/{id}/load-characteristics", versionId)
                        .with(contentAdmin()).contentType("application/json").content("""
                                [{"movementPlane":"SAGITTAL","contractionType":"MIXED",
                                  "rangeOfMotion":"FULL","characteristicType":"DYNAMIC"}]
                                """))
                .andExpect(status().isOk());
    }

    private UUID addEvidence(UUID versionId) throws Exception {
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/evidence", versionId)
                        .with(contentAdmin()).contentType("application/json").content(evidenceRequest()))
                .andExpect(status().isOk());
        return jdbc.queryForObject("""
                SELECT id FROM exercise_catalog.evidence_source
                WHERE exercise_version_id = ? ORDER BY created_at DESC LIMIT 1
                """, UUID.class, versionId);
    }

    private org.springframework.test.web.servlet.ResultActions addContribution(
            UUID versionId, UUID structureId, UUID evidenceId, String calculationRole,
            String variant, String sideRule, String high) throws Exception {
        return mvc.perform(post("/api/v1/admin/exercises/versions/{id}/contributions", versionId)
                .with(contentAdmin()).contentType("application/json").content("""
                        {"anatomicalStructureId":"%s","role":"PRIMARY","loadChannel":"DYN_EXU",
                         "contributionBand":"HIGH","coefficientLow":0.200000,"coefficientHigh":%s,
                         "confidenceClass":"MODERATE","evidenceGrade":"EDITORIAL_REVIEW",
                         "calculationRole":"%s","variantCondition":"%s","sideRule":"%s",
                         "evidenceSourceIds":["%s"]}
                        """.formatted(structureId, high, calculationRole, variant, sideRule, evidenceId)));
    }

    private UUID createPublishedAnatomy(String code, String type) throws Exception {
        UUID id = createAnatomy(code, type);
        publishAnatomy(id);
        return id;
    }

    private UUID createAnatomy(String code, String type) throws Exception {
        mvc.perform(post("/api/v1/admin/anatomical-structures").with(contentAdmin())
                        .contentType("application/json").content("""
                                {"code":"%s","type":"%s","displayName":"%s",
                                 "sidePolicy":"LEFT_RIGHT","taxonomyVersion":1}
                                """.formatted(code, type, code)))
                .andExpect(status().isOk());
        return jdbc.queryForObject(
                "SELECT id FROM anatomy_reference.anatomical_structure WHERE code = ?", UUID.class, code);
    }

    private void addAnatomyRelation(UUID parent, UUID child) throws Exception {
        mvc.perform(post("/api/v1/admin/anatomical-structures/relations").with(contentAdmin())
                        .contentType("application/json").content("""
                                {"parentId":"%s","childId":"%s","relationType":"PART_OF"}
                                """.formatted(parent, child)))
                .andExpect(status().isOk());
    }

    private void publishAnatomy(UUID id) throws Exception {
        mvc.perform(post("/api/v1/admin/anatomical-structures/{id}/publish", id).with(contentAdmin()))
                .andExpect(status().isOk());
    }

    private int createVersionConcurrently(UUID exerciseId, CountDownLatch ready,
                                          CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post("/api/v1/admin/exercises/{id}/versions", exerciseId)
                        .with(contentAdmin()).contentType("application/json").content(versionCommand()))
                .andReturn().getResponse().getStatus();
    }

    private static String createExerciseRequest(String name) {
        return "{\"canonicalName\":\"%s\",\"version\":%s}".formatted(name, versionCommand());
    }

    private static String versionCommand() {
        return """
                {"instruction":"Perform the movement with controlled tempo and stable posture.",
                 "mediaReference":"s3://catalog/exercise.mp4",
                 "movementPatterns":["SQUAT","MOBILITY"],"stimulusType":"STRENGTH",
                 "fatigueProfile":"MODERATE","technicalLevel":"FOUNDATIONAL",
                 "environment":"ANY","requiredEquipment":["band"],
                 "contraindicationTags":["ACUTE_KNEE_PAIN"]}
                """;
    }

    private static String evidenceRequest() {
        return """
                {"citation":"Editorial biomechanics review","sourceUri":"https://example.test/evidence/1",
                 "evidenceGrade":"EDITORIAL_REVIEW"}
                """;
    }

    private static JwtRequestPostProcessor participant(String subject) {
        return jwt().jwt(builder -> builder.subject(subject).audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
    }

    private static JwtRequestPostProcessor contentAdmin() {
        return jwt().jwt(builder -> builder.subject("catalog-admin").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN"));
    }

    private record CreatedExercise(UUID exerciseId, UUID versionId) {
    }
}
