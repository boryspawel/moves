package com.motionecosystem.trainingexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.motionecosystem.support.PostgresTestConfiguration;
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
class TrainingPlanningExecutionIntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FilterChainProxy securityFilterChain;
    @Autowired
    JdbcTemplate jdbc;

    MockMvc mvc;
    UUID participantId;
    UUID otherParticipantId;
    UUID specialistId;
    UUID foreignSpecialistId;
    UUID exerciseVersionId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        participantId = account("participant", "PARTICIPANT");
        otherParticipantId = account("other-participant", "PARTICIPANT");
        specialistId = account("specialist", "SPECIALIST");
        foreignSpecialistId = account("foreign-specialist", "SPECIALIST");
        jdbc.update("""
                INSERT INTO specialist.participant_specialist_relationship
                    (id, specialist_account_id, participant_account_id, status, activated_at)
                VALUES (?, ?, ?, 'ACTIVE', now())
                """, UUID.randomUUID(), specialistId, participantId);
        exerciseVersionId = publishedExerciseVersion();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.audit_event,
                    training_execution.execution_alert,
                    training_execution.execution_correction,
                    training_execution.pain_difficulty_report,
                    training_execution.exercise_result,
                    training_execution.session_execution,
                    training_planning.exercise_prescription,
                    training_planning.planned_session,
                    training_planning.microcycle,
                    training_planning.training_cycle,
                    training_planning.training_plan,
                    training_planning.training_goal,
                    specialist.participant_specialist_relationship,
                    safety.participant_restriction,
                    safety.readiness_check_in,
                    exercise_catalog.exercise_version_contraindication,
                    exercise_catalog.exercise_version_equipment,
                    exercise_catalog.exercise_version,
                    exercise_catalog.exercise,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void activeSpecialistAssignsExactVersionAndParticipantDeclaresIdempotentExecution() throws Exception {
        mvc.perform(post("/api/v1/training-plans")
                        .with(role("specialist", "SPECIALIST"))
                        .contentType("application/json")
                        .content(planRequest("SELF_GUIDED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.mode").value("SPECIALIST_ASSIGNED"))
                .andExpect(jsonPath("$.session.kind").value("SELF_GUIDED"))
                .andExpect(jsonPath("$.prescriptions[0].exerciseVersionId")
                        .value(exerciseVersionId.toString()));

        UUID sessionId = jdbc.queryForObject("SELECT id FROM training_planning.planned_session", UUID.class);
        UUID prescriptionId = jdbc.queryForObject(
                "SELECT id FROM training_planning.exercise_prescription", UUID.class);
        assertThat(jdbc.queryForObject(
                "SELECT exercise_version_id FROM training_planning.exercise_prescription", UUID.class))
                .isEqualTo(exerciseVersionId);

        mvc.perform(get("/api/v1/planned-sessions").with(role("participant", "PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sessionId.toString()))
                .andExpect(jsonPath("$[0].prescriptions[0].id").value(prescriptionId.toString()));

        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "execution-1")
                        .contentType("application/json")
                        .content(executionRequest(prescriptionId, false, 3, 7)))
                .andExpect(status().isBadRequest());

        String declared = executionRequest(prescriptionId, true, 3, 7);
        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "execution-1")
                        .contentType("application/json")
                        .content(declared))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredCompletion").value(true))
                .andExpect(jsonPath("$.painLevel").value(3))
                .andExpect(jsonPath("$.difficultyLevel").value(7))
                .andExpect(jsonPath("$.alerts[0]").value("PAIN_REPORTED"));

        UUID executionId = jdbc.queryForObject(
                "SELECT id FROM training_execution.session_execution", UUID.class);
        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "execution-1")
                        .contentType("application/json")
                        .content(declared))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(executionId.toString()));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.session_execution", Long.class)).isEqualTo(1);

        mvc.perform(get("/api/v1/specialist/participants/{id}/executions", participantId)
                        .with(role("specialist", "SPECIALIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(executionId.toString()))
                .andExpect(jsonPath("$[0].alerts[0]").value("PAIN_REPORTED"));

        mvc.perform(post("/api/v1/session-executions/{id}/corrections", executionId)
                        .with(role("specialist", "SPECIALIST"))
                        .contentType("application/json")
                        .content("{\"reason\":\"Participant clarified the scale\",\"correctedPainLevel\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.painLevel").value(3))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].correctedPainLevel").value(2));
        assertThat(jdbc.queryForObject(
                "SELECT pain_level FROM training_execution.pain_difficulty_report", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.execution_correction", Long.class)).isEqualTo(1);
    }

    @Test
    void foreignSpecialistIsRejectedAndExplicitRestrictionHardBlocksExecution() throws Exception {
        mvc.perform(post("/api/v1/training-plans")
                        .with(role("foreign-specialist", "SPECIALIST"))
                        .contentType("application/json")
                        .content(planRequest("SELF_GUIDED")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/training-plans")
                        .with(role("specialist", "SPECIALIST"))
                        .contentType("application/json")
                        .content(planRequest("OFFLINE_APPOINTMENT")))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_planning.planned_session", Long.class)).isZero();

        createPlan();

        UUID sessionId = jdbc.queryForObject("SELECT id FROM training_planning.planned_session", UUID.class);
        UUID prescriptionId = jdbc.queryForObject(
                "SELECT id FROM training_planning.exercise_prescription", UUID.class);
        jdbc.update("""
                INSERT INTO safety.participant_restriction
                    (id, account_id, contraindication_tag, recorded_at)
                VALUES (?, ?, 'ACUTE_KNEE_PAIN', now())
                """, UUID.randomUUID(), participantId);

        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "blocked-execution")
                        .contentType("application/json")
                        .content(executionRequest(prescriptionId, true, 0, 4)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.session_execution", Long.class)).isZero();

        mvc.perform(get("/api/v1/specialist/participants/{id}/executions", participantId)
                        .with(role("foreign-specialist", "SPECIALIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherParticipantCannotExecuteAssignedSession() throws Exception {
        createPlan();
        UUID sessionId = sessionId();
        UUID prescriptionId = prescriptionId();

        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("other-participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "foreign-participant-execution")
                        .contentType("application/json")
                        .content(executionRequest(prescriptionId, true, 0, 4)))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.session_execution", Long.class)).isZero();
    }

    @Test
    void concurrentRetryWithSameKeyReturnsStableExecution() throws Exception {
        createPlan();
        UUID sessionId = sessionId();
        String request = executionRequest(prescriptionId(), true, 0, 4);

        List<Integer> statuses = concurrentDeclarations(sessionId, request, "same-key", "same-key");

        assertThat(statuses).containsExactlyInAnyOrder(200, 200);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.session_execution", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT id) FROM training_execution.session_execution", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentDifferentKeysAllowOnlyOneSuccessfulExecution() throws Exception {
        createPlan();
        UUID sessionId = sessionId();
        String request = executionRequest(prescriptionId(), true, 0, 4);

        List<Integer> statuses = concurrentDeclarations(sessionId, request, "first-key", "second-key");

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.session_execution", Long.class)).isEqualTo(1);
    }

    @Test
    void openApiPublishesPlanningAndExecutionContracts() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/training-plans']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planned-sessions/{sessionId}/executions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/session-executions/{executionId}/corrections']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/specialist/participants/{participantAccountId}/executions']")
                        .exists());
    }

    private UUID account(String subject, String profileType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_access.principal_account
                    (id, external_subject, status, profile_type, created_at, version)
                VALUES (?, ?, 'ACTIVE', ?, now(), 0)
                """, id, subject, profileType);
        return id;
    }

    private UUID publishedExerciseVersion() {
        UUID exerciseId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise
                    (id, canonical_name, created_at, created_by_subject)
                VALUES (?, 'Supported squat', now(), 'catalog-admin')
                """, exerciseId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version
                    (id, exercise_id, version_number, status, instruction, movement_pattern,
                     stimulus_type, fatigue_profile, technical_level, environment,
                     created_at, published_at, version)
                VALUES (?, ?, 1, 'PUBLISHED', 'Perform a controlled supported squat.', 'SQUAT',
                        'STRENGTH', 'MODERATE', 'FOUNDATIONAL', 'ANY', now(), now(), 0)
                """, versionId, exerciseId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version_contraindication
                    (exercise_version_id, contraindication_tag)
                VALUES (?, 'ACUTE_KNEE_PAIN')
                """, versionId);
        return versionId;
    }

    private void createPlan() throws Exception {
        mvc.perform(post("/api/v1/training-plans")
                        .with(role("specialist", "SPECIALIST"))
                        .contentType("application/json")
                        .content(planRequest("SELF_GUIDED")))
                .andExpect(status().isOk());
    }

    private UUID sessionId() {
        return jdbc.queryForObject("SELECT id FROM training_planning.planned_session", UUID.class);
    }

    private UUID prescriptionId() {
        return jdbc.queryForObject("SELECT id FROM training_planning.exercise_prescription", UUID.class);
    }

    private List<Integer> concurrentDeclarations(UUID sessionId, String request,
                                                 String firstKey, String secondKey) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> declarationStatus(
                    sessionId, request, firstKey, ready, start));
            Future<Integer> second = executor.submit(() -> declarationStatus(
                    sessionId, request, secondKey, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private int declarationStatus(UUID sessionId, String request, String key,
                                  CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(request))
                .andReturn().getResponse().getStatus();
    }

    private String planRequest(String sessionKind) {
        return """
                {
                  "participantAccountId":"%s",
                  "goalName":"Build movement consistency",
                  "planName":"Foundation plan",
                  "cycleName":"Foundation cycle",
                  "microcycleName":"Week one",
                  "sessionTitle":"Supported strength",
                  "sessionKind":"%s",
                  "prescriptions":[{
                    "exerciseVersionId":"%s",
                    "targetSets":3,
                    "targetRepetitions":8,
                    "targetLoadKg":0,
                    "notes":"Stop if the explicit restriction applies"
                  }]
                }
                """.formatted(participantId, sessionKind, exerciseVersionId);
    }

    private static String executionRequest(UUID prescriptionId, boolean declared, int pain, int difficulty) {
        return """
                {
                  "declaredCompletion":%s,
                  "painLevel":%d,
                  "difficultyLevel":%d,
                  "note":"Participant-reported after the session",
                  "results":[{
                    "exercisePrescriptionId":"%s",
                    "actualRepetitions":8,
                    "actualLoadKg":0
                  }]
                }
                """.formatted(declared, pain, difficulty, prescriptionId);
    }

    private static JwtRequestPostProcessor role(String subject, String role) {
        return jwt().jwt(builder -> builder.subject(subject).audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
