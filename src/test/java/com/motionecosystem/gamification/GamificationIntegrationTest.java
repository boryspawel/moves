package com.motionecosystem.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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
class GamificationIntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FilterChainProxy securityFilterChain;
    @Autowired
    JdbcTemplate jdbc;

    MockMvc mvc;
    UUID participantId;
    UUID microcycleId;
    UUID exerciseVersionId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        participantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_access.principal_account
                    (id, external_subject, status, profile_type, created_at, version)
                VALUES (?, 'game-participant', 'ACTIVE', 'PARTICIPANT', now(), 0)
                """, participantId);
        exerciseVersionId = publishedExerciseVersion();
        microcycleId = hierarchy();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.audit_event,
                    gamification.ranking_projection,
                    gamification.point_ledger_entry,
                    gamification.gamification_profile,
                    gamification.point_rule_version,
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
                    safety.participant_restriction,
                    exercise_catalog.exercise_version_contraindication,
                    exercise_catalog.exercise_version_equipment,
                    exercise_catalog.exercise_version,
                    exercise_catalog.exercise,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void optInLedgerDiminishingIdempotencyReversalAndRebuildStayPrivate() throws Exception {
        publishRule();
        UUID beforeOptIn = execution(plannedSession());

        mvc.perform(post("/api/v1/gamification/executions/{id}/qualifications", beforeOptIn)
                        .with(participant())
                        .header("Idempotency-Key", "before-opt-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.outcome").value("NOT_OPTED_IN"));
        assertThat(ledgerCount()).isZero();

        mvc.perform(put("/api/v1/gamification/me/profile")
                        .with(participant())
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"pseudonym\":\"QuietMover\",\"rankingVisible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        UUID first = execution(plannedSession());
        mvc.perform(post("/api/v1/gamification/executions/{id}/qualifications", first)
                        .with(participant())
                        .header("Idempotency-Key", "award-first"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(50))
                .andExpect(jsonPath("$.outcome").value("SESSION_COMPLETION"));
        mvc.perform(post("/api/v1/gamification/executions/{id}/qualifications", first)
                        .with(participant())
                        .header("Idempotency-Key", "different-retry-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(50));
        assertThat(ledgerCount()).isEqualTo(1);

        UUID repeated = execution(plannedSession());
        mvc.perform(post("/api/v1/gamification/executions/{id}/qualifications", repeated)
                        .with(participant())
                        .header("Idempotency-Key", "award-repeated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(25))
                .andExpect(jsonPath("$.outcome").value("DIMINISHING_RETURN"));

        String privateProgress = mvc.perform(get("/api/v1/gamification/me").with(participant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(75))
                .andReturn().getResponse().getContentAsString();
        assertThat(privateProgress).doesNotContainIgnoringCase("pain", "difficulty", "medical", "note");

        mvc.perform(get("/api/v1/gamification/ranking").with(participant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pseudonym").value("QuietMover"))
                .andExpect(jsonPath("$[0].points").value(75));

        UUID firstEntry = jdbc.queryForObject("""
                SELECT id FROM gamification.point_ledger_entry
                WHERE source_execution_id = ? AND entry_type = 'AWARD'
                """, UUID.class, first);
        mvc.perform(post("/api/v1/admin/gamification/ledger/{id}/reversals", firstEntry)
                        .with(admin())
                        .contentType("application/json")
                        .content("{\"reason\":\"Duplicate imported completion\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(-50));
        assertThat(jdbc.queryForObject(
                "SELECT points FROM gamification.point_ledger_entry WHERE id = ?", Integer.class, firstEntry))
                .isEqualTo(50);
        assertThat(jdbc.queryForObject("""
                SELECT explanation FROM gamification.point_ledger_entry WHERE reverses_entry_id = ?
                """, String.class, firstEntry)).isEqualTo("Duplicate imported completion");

        jdbc.update("DELETE FROM gamification.ranking_projection");
        mvc.perform(get("/api/v1/gamification/ranking").with(participant()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/admin/gamification/ranking/rebuild").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points").value(25));

        mvc.perform(put("/api/v1/gamification/me/profile")
                        .with(participant())
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"pseudonym\":\"QuietMover\",\"rankingVisible\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/gamification/ranking").with(participant()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void hardBlockedSessionCannotProduceAnExecutionOrPoints() throws Exception {
        publishRule();
        mvc.perform(put("/api/v1/gamification/me/profile")
                        .with(participant())
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"pseudonym\":\"SafeMover\",\"rankingVisible\":false}"))
                .andExpect(status().isOk());
        UUID sessionId = plannedSession();
        UUID prescriptionId = jdbc.queryForObject("""
                SELECT id FROM training_planning.exercise_prescription WHERE planned_session_id = ?
                """, UUID.class, sessionId);
        jdbc.update("""
                INSERT INTO safety.participant_restriction
                    (id, account_id, contraindication_tag, recorded_at)
                VALUES (?, ?, 'ACUTE_KNEE_PAIN', now())
                """, UUID.randomUUID(), participantId);

        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(participant())
                        .header("Idempotency-Key", "blocked-session")
                        .contentType("application/json")
                        .content("""
                                {"declaredCompletion":true,"painLevel":0,"difficultyLevel":3,
                                 "results":[{"exercisePrescriptionId":"%s","actualRepetitions":8}]}
                                """.formatted(prescriptionId)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.session_execution", Long.class)).isZero();
        assertThat(ledgerCount()).isZero();
    }

    @Test
    void openApiContainsRealGamificationContracts() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/gamification/me']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/gamification/executions/{executionId}/qualifications']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/gamification/ranking']").exists());
    }

    private void publishRule() throws Exception {
        mvc.perform(post("/api/v1/admin/gamification/rules")
                        .with(admin())
                        .contentType("application/json")
                        .content("""
                                {"versionName":"verified-source-inspired-v1","basePoints":50,
                                 "dailyLimit":500,"weeklyLimit":1000,"cooldownSeconds":0,
                                 "repeatWindowDays":5,"fullRewardOccurrences":1,
                                 "reducedRewardPercent":50}
                                """))
                .andExpect(status().isOk());
    }

    private UUID hierarchy() {
        UUID goal = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID cycle = UUID.randomUUID();
        UUID microcycle = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_planning.training_goal
                    (id, participant_account_id, name, created_by_account_id, created_at)
                VALUES (?, ?, 'Consistency', ?, now())
                """, goal, participantId, participantId);
        jdbc.update("""
                INSERT INTO training_planning.training_plan
                    (id, goal_id, participant_account_id, created_by_account_id, name, plan_mode, status, created_at)
                VALUES (?, ?, ?, ?, 'Self test plan', 'SELF_DIRECTED', 'ACTIVE', now())
                """, plan, goal, participantId, participantId);
        jdbc.update("INSERT INTO training_planning.training_cycle (id, plan_id, sequence_number, name) VALUES (?, ?, 1, 'Cycle')",
                cycle, plan);
        jdbc.update("INSERT INTO training_planning.microcycle (id, cycle_id, sequence_number, name) VALUES (?, ?, 1, 'Week')",
                microcycle, cycle);
        return microcycle;
    }

    private UUID plannedSession() {
        UUID session = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_planning.planned_session
                    (id, microcycle_id, participant_account_id, title, session_kind, status, assigned_at)
                VALUES (?, ?, ?, 'Session', 'SELF_GUIDED', 'ASSIGNED', now())
                """, session, microcycleId, participantId);
        jdbc.update("""
                INSERT INTO training_planning.exercise_prescription
                    (id, planned_session_id, exercise_version_id, position, target_repetitions)
                VALUES (?, ?, ?, 1, 8)
                """, UUID.randomUUID(), session, exerciseVersionId);
        return session;
    }

    private UUID execution(UUID sessionId) {
        UUID execution = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_execution.session_execution
                    (id, planned_session_id, participant_account_id, declared_completion, idempotency_key, recorded_at)
                VALUES (?, ?, ?, true, ?, now())
                """, execution, sessionId, participantId, "fixture-" + execution);
        jdbc.update("""
                INSERT INTO training_execution.pain_difficulty_report
                    (id, session_execution_id, pain_level, difficulty_level, reported_at)
                VALUES (?, ?, 0, 3, now())
                """, UUID.randomUUID(), execution);
        return execution;
    }

    private UUID publishedExerciseVersion() {
        UUID exercise = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise
                    (id, canonical_name, created_at, created_by_subject)
                VALUES (?, 'Supported squat', now(), 'admin')
                """, exercise);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version
                    (id, exercise_id, version_number, status, instruction, movement_pattern,
                     stimulus_type, fatigue_profile, technical_level, environment,
                     created_at, published_at, version)
                VALUES (?, ?, 1, 'PUBLISHED', 'Controlled movement instruction', 'SQUAT',
                        'STRENGTH', 'MODERATE', 'FOUNDATIONAL', 'ANY', now(), now(), 0)
                """, version, exercise);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version_contraindication
                    (exercise_version_id, contraindication_tag)
                VALUES (?, 'ACUTE_KNEE_PAIN')
                """, version);
        return version;
    }

    private long ledgerCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gamification.point_ledger_entry", Long.class);
    }

    private static JwtRequestPostProcessor participant() {
        return jwt().jwt(builder -> builder.subject("game-participant").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(builder -> builder.subject("game-admin").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_GAMIFICATION_ADMIN"));
    }
}
