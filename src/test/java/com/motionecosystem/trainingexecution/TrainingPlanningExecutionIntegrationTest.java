package com.motionecosystem.trainingexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.adherence.TodayAgendaService;
import com.motionecosystem.audit.api.TransactionalOutbox.OutboxMessage;
import com.motionecosystem.support.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired
    ExecutionProjectionService projections;
    @Autowired
    TodayAgendaService today;
    @Autowired
    SessionExecutionAttemptRepository attempts;
    @Autowired
    EntityManager entityManager;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    tools.jackson.databind.ObjectMapper objectMapper;

    MockMvc mvc;
    UUID participantId;
    UUID otherParticipantId;
    UUID specialistId;
    UUID foreignSpecialistId;
    UUID exerciseVersionId;
    UUID executionStructureId;
    String p4Subject;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        participantId = account("participant", "PARTICIPANT");
        otherParticipantId = account("other-participant", "PARTICIPANT");
        specialistId = account("specialist", "SPECIALIST");
        foreignSpecialistId = account("foreign-specialist", "SPECIALIST");
        participantRecord(participantId);
        participantRecord(otherParticipantId);
        jdbc.update("""
                INSERT INTO specialist.participant_specialist_relationship
                    (id, specialist_account_id, participant_id, status, activated_at)
                VALUES (?, ?, ?, 'ACTIVE', now())
                """, UUID.randomUUID(), specialistId, participantId);
        exerciseVersionId = publishedExerciseVersion();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.outbox_event,
                    audit.audit_event,
                    training_execution.executed_load_aggregate,
                    training_execution.execution_alert_history,
                    training_execution.post_24h_response,
                    training_execution.execution_projection_receipt,
                    training_execution.execution_qualification,
                    training_execution.executed_load_observation,
                    training_execution.execution_alert,
                    training_execution.execution_correction,
                    training_execution.pain_difficulty_report,
                    training_execution.exercise_result,
                    training_execution.session_execution,
                    training_execution.session_execution_attempt_fact,
                    training_execution.session_execution_attempt,
                    training_planning.exercise_prescription,
                    training_planning.planned_session,
                    training_planning.microcycle,
                    training_planning.training_cycle,
                    training_planning.training_plan,
                    training_planning.training_goal,
                    specialist.participant_specialist_relationship,
                    safety.participant_restriction,
                    safety.readiness_check_in,
                    participant.participant_access_link,
                    participant.participant_record,
                    exercise_catalog.exercise_version_contraindication,
                    exercise_catalog.exercise_version_equipment,
                    exercise_catalog.exercise_contribution,
                    exercise_catalog.exercise_version,
                    exercise_catalog.exercise,
                    anatomy_reference.anatomical_structure,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void activeSpecialistAssignsExactVersionAndParticipantDeclaresIdempotentExecution() throws Exception {
        createPlan();

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
                        .header("Idempotency-Key", "correction-1")
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
    void participantReadsOnlyOwnBoundedExecutionAttemptHistory() throws Exception {
        createPlan();
        UUID sessionId = sessionId();
        UUID revisionId = jdbc.queryForObject("SELECT id FROM training_planning.plan_revision", UUID.class);
        attempts.saveAndFlush(new SessionExecutionAttempt(participantId, sessionId, revisionId, "STANDARD", "history-attempt", Instant.now()));

        mvc.perform(get("/api/v1/participant/execution-history")
                        .param("from", Instant.now().minusSeconds(3600).toString())
                        .param("to", Instant.now().plusSeconds(3600).toString())
                        .param("limit", "1")
                        .with(role("participant", "PARTICIPANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plannedSessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$[0].planRevisionId").value(revisionId.toString()));
        mvc.perform(get("/api/v1/participant/execution-history")
                        .param("from", Instant.now().minusSeconds(3600).toString())
                        .param("to", Instant.now().plusSeconds(3600).toString())
                        .with(role("other-participant", "PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void partialFactCompletionProjectsDetailedActualDoseInRecordedOrder() throws Exception {
        P4Fixture fixture = p4Fixture();

        String started = mvc.perform(post("/api/v1/participant/session-attempts")
                        .with(role(p4Subject, "PARTICIPANT"))
                        .header("Idempotency-Key", "p4-start")
                        .contentType("application/json")
                        .content("""
                                {"plannedSessionId":"%s","planRevisionId":"%s","selectedVariantType":"STANDARD"}
                                """.formatted(fixture.sessionId(), fixture.revisionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STARTED"))
                .andReturn().getResponse().getContentAsString();
        UUID attemptId = UUID.fromString(objectMapper.readTree(started).path("attemptId").asText());

        String fact = """
                {"exercisePrescriptionId":"%s","outcome":"PARTIAL","reason":"FATIGUE",
                 "result":{"exercisePrescriptionId":"%s","actualSets":3,"observationMode":"DECLARED",
                 "actualSetDetails":[{"repetitions":10},{"repetitions":10},{"repetitions":7}]}}
                """.formatted(fixture.prescriptionId(), fixture.prescriptionId());
        mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/facts", attemptId)
                        .with(role(p4Subject, "PARTICIPANT"))
                        .contentType("application/json").content(fact))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STARTED"))
                .andExpect(jsonPath("$.facts[0].outcome").value("PARTIAL"))
                .andExpect(jsonPath("$.facts[0].result.actualSetDetails[0].repetitions").value(10))
                .andExpect(jsonPath("$.facts[0].result.actualSetDetails[1].repetitions").value(10))
                .andExpect(jsonPath("$.facts[0].result.actualSetDetails[2].repetitions").value(7));

        mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/finish", attemptId)
                        .with(role(p4Subject, "PARTICIPANT"))
                        .header("Idempotency-Key", "p4-finish")
                        .contentType("application/json")
                        .content("{\"intent\":\"COMPLETE\",\"painLevel\":0,\"difficultyLevel\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PARTIAL"))
                .andExpect(jsonPath("$.declaredCompletion").value(false))
                .andExpect(jsonPath("$.results[0].actualSetDetails[0].repetitions").value(10))
                .andExpect(jsonPath("$.results[0].actualSetDetails[1].repetitions").value(10))
                .andExpect(jsonPath("$.results[0].actualSetDetails[2].repetitions").value(7));

        UUID executionId = transactionTemplate.execute(status -> (UUID) entityManager.createNativeQuery(
                "SELECT id FROM training_execution.session_execution WHERE attempt_id=:attemptId")
                .setParameter("attemptId", attemptId).getSingleResult());
        assertThat(projections.consume(declarationEventNative(executionId)).created()).isTrue();
        java.math.BigDecimal projectedActual = transactionTemplate.execute(status -> (java.math.BigDecimal) entityManager
                .createNativeQuery("SELECT value_high FROM training_execution.executed_load_observation WHERE session_execution_id=:executionId AND channel='DYN_EXU'")
                .setParameter("executionId", executionId).getSingleResult());
        assertThat(projectedActual).isEqualByComparingTo("27.000000");
    }

    @Test
    void startsRequestedSecondActivePlanRevisionForLinkedParticipantAccount() throws Exception {
        P4Fixture first = p4Fixture();
        P4Fixture second = activateSecondP4Plan(first);

        var agenda = today.today(p4Subject);
        assertThat(agenda.activePlans()).extracting(TodayAgendaService.ActivePlanView::revisionId)
                .contains(first.revisionId(), second.revisionId());
        assertThat(agenda.sessions()).extracting(TodayAgendaService.AgendaSessionView::sessionId)
                .contains(first.sessionId(), second.sessionId());
        assertThat(agenda.sessions()).filteredOn(item -> item.sessionId().equals(second.sessionId()))
                .extracting(TodayAgendaService.AgendaSessionView::planRevisionId).containsExactly(second.revisionId());

        startP4(second, "p4-second-active-plan")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedSessionId").value(second.sessionId().toString()))
                .andExpect(jsonPath("$.planRevisionId").value(second.revisionId().toString()));
    }

    @Test
    void factsAreAppendOnlyLatestWinsAndTerminalAttemptRejectsFurtherFacts() throws Exception {
        P4Fixture fixture = p4Fixture();
        UUID attemptId = startP4Attempt(fixture, "revision-start");
        mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/facts", attemptId)
                        .with(role(p4Subject, "PARTICIPANT")).contentType("application/json")
                        .content(fact(fixture.prescriptionId(), "PERFORMED", null, "{\"actualSets\":1}")))
                .andExpect(status().isBadRequest());
        recordP4Fact(attemptId, fixture.prescriptionId(), "PERFORMED", null, "{\"actualRepetitions\":10}");
        mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/facts", attemptId)
                        .with(role("other-participant", "PARTICIPANT")).contentType("application/json")
                        .content(fact(fixture.prescriptionId(), "PARTIAL", "FATIGUE", "{\"actualRepetitions\":7}")))
                .andExpect(status().isNotFound());
        recordP4Fact(attemptId, fixture.prescriptionId(), "PARTIAL", "FATIGUE", "{\"actualRepetitions\":7}");
        mvc.perform(get("/api/v1/participant/session-attempts/{attemptId}", attemptId).with(role(p4Subject, "PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.facts.length()").value(1))
                .andExpect(jsonPath("$.facts[0].revisionNumber").value(2)).andExpect(jsonPath("$.facts[0].outcome").value("PARTIAL"));
        finish(attemptId, "revision-finish", "COMPLETE", null).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PARTIAL"));
        mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/facts", attemptId)
                        .with(role(p4Subject, "PARTICIPANT")).contentType("application/json")
                        .content(fact(fixture.prescriptionId(), "PERFORMED", null, "{\"actualRepetitions\":10}")))
                .andExpect(status().isConflict());
    }

    @Test
    void stoppedAttemptsAreTerminalWithoutCompletionQualificationOrRestart() throws Exception {
        P4Fixture empty = p4Fixture();
        UUID emptyAttempt = startP4Attempt(empty, "stop-empty-start");
        finish(emptyAttempt, "stop-empty-finish", "STOP", "TIME").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("STOPPED")).andExpect(jsonPath("$.declaredCompletion").value(false));
        startP4(empty, "stop-empty-restart").andExpect(status().isConflict());

        P4Fixture recorded = p4Fixture();
        UUID recordedAttempt = startP4Attempt(recorded, "stop-work-start");
        recordP4Fact(recordedAttempt, recorded.prescriptionId(), "PERFORMED", null, "{\"actualRepetitions\":10}");
        finish(recordedAttempt, "stop-work-finish", "STOP", "FATIGUE").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("STOPPED")).andExpect(jsonPath("$.declaredCompletion").value(false));
        startP4(recorded, "stop-work-restart").andExpect(status().isConflict());
        Long qualifications = transactionTemplate.execute(status -> ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM training_execution.execution_qualification q
                JOIN training_execution.session_execution e ON e.id=q.session_execution_id
                WHERE e.attempt_id=:attemptId
                """).setParameter("attemptId", recordedAttempt).getSingleResult()).longValue());
        assertThat(qualifications).isZero();
    }

    @Test
    void completeRequiresFactsAndSeparatesCompletedFromSkippedTerminalOutcomes() throws Exception {
        P4Fixture missing = p4Fixture();
        UUID missingAttempt = startP4Attempt(missing, "missing-start");
        finish(missingAttempt, "missing-finish", "COMPLETE", null).andExpect(status().isConflict());

        P4Fixture completed = p4Fixture();
        UUID completedAttempt = startP4Attempt(completed, "completed-start");
        recordP4Fact(completedAttempt, completed.prescriptionId(), "PERFORMED", null, "{\"actualRepetitions\":10}");
        finish(completedAttempt, "completed-finish", "COMPLETE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPLETED")).andExpect(jsonPath("$.declaredCompletion").value(true));

        P4Fixture skipped = p4Fixture();
        UUID skippedAttempt = startP4Attempt(skipped, "skipped-start");
        recordP4Fact(skippedAttempt, skipped.prescriptionId(), "SKIPPED", "OTHER", "{\"skipped\":true}");
        finish(skippedAttempt, "skipped-finish", "COMPLETE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SKIPPED")).andExpect(jsonPath("$.declaredCompletion").value(false));
        mvc.perform(get("/api/v1/participant/execution-history").param("from", Instant.now().minusSeconds(3600).toString())
                        .param("to", Instant.now().plusSeconds(3600).toString()).with(role(p4Subject, "PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].outcome").value("SKIPPED"));
    }

    @Test
    void durationOnlyPinnedPrescriptionRejectsRepetitionsAndAcceptsDuration() throws Exception {
        P4Fixture fixture = p4Fixture(true);
        UUID attemptId = startP4Attempt(fixture, "duration-start");
        mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/facts", attemptId)
                        .with(role(p4Subject, "PARTICIPANT")).contentType("application/json")
                        .content(fact(fixture.prescriptionId(), "PERFORMED", null, "{\"actualRepetitions\":10}")))
                .andExpect(status().isBadRequest());
        recordP4Fact(attemptId, fixture.prescriptionId(), "PERFORMED", null, "{\"actualDurationSeconds\":30}");
    }

    @Test
    void foreignSpecialistIsRejectedAndLegacyTagsDoNotDriveNewExecutionSafety() throws Exception {
        mvc.perform(post("/api/v1/training-plans")
                        .with(role("foreign-specialist", "SPECIALIST"))
                        .contentType("application/json")
                        .content(planRequest("SELF_GUIDED")))
                .andExpect(status().isGone());

        mvc.perform(post("/api/v1/training-plans")
                        .with(role("specialist", "SPECIALIST"))
                        .contentType("application/json")
                        .content(planRequest("OFFLINE_APPOINTMENT")))
                .andExpect(status().isGone());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_planning.planned_session", Long.class)).isZero();

        createPlan();

        UUID sessionId = jdbc.queryForObject("SELECT id FROM training_planning.planned_session", UUID.class);
        UUID prescriptionId = jdbc.queryForObject(
                "SELECT id FROM training_planning.exercise_prescription", UUID.class);
        jdbc.update("""
                INSERT INTO safety.participant_restriction
                    (id, account_id, participant_id, contraindication_tag, recorded_at)
                VALUES (?, ?, ?, 'ACUTE_KNEE_PAIN', now())
                """, UUID.randomUUID(), participantId, participantId);

        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "legacy-tag-execution")
                        .contentType("application/json")
                        .content(executionRequest(prescriptionId, true, 0, 4)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.session_execution", Long.class)).isEqualTo(1);

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
    void actualDoseIsProjectedOnceAndCorrectionRebuildsItWithQualificationReversal() throws Exception {
        UUID structureId = executionContribution();
        createPlan();
        UUID sessionId = sessionId();
        UUID prescriptionId = prescriptionId();
        String request = """
                {
                  "declaredCompletion":true,
                  "painLevel":0,
                  "difficultyLevel":6,
                  "sessionRpe":7,
                  "observationMode":"DECLARED",
                  "results":[{
                    "exercisePrescriptionId":"%s",
                    "actualSets":2,
                    "actualRepetitions":5,
                    "actualDurationSeconds":30,
                    "actualContacts":6,
                    "actualExternalLoadValue":12.5,
                    "actualExternalLoadUnit":"kg",
                    "side":"LEFT",
                    "modified":true,
                    "observationMode":"DEVICE"
                  }]
                }
                """.formatted(prescriptionId);

        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "actual-dose")
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].exerciseVersionId").value(exerciseVersionId.toString()))
                .andExpect(jsonPath("$.results[0].actualSets").value(2))
                .andExpect(jsonPath("$.results[0].side").value("LEFT"))
                .andExpect(jsonPath("$.sessionRpe").value(7))
                .andExpect(jsonPath("$.results[0].observationMode").value("DEVICE"));

        UUID executionId = jdbc.queryForObject("SELECT id FROM training_execution.session_execution", UUID.class);
        OutboxMessage declaration = declarationEvent(executionId);
        assertThat(projections.consume(declaration).created()).isTrue();
        assertThat(projections.consume(declaration).created()).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT value_low FROM training_execution.executed_load_observation
                WHERE anatomical_structure_id=? AND channel='DYN_EXU'
                """, java.math.BigDecimal.class, structureId)).isEqualByComparingTo("5.000000");
        assertThat(jdbc.queryForObject("""
                SELECT value_high FROM training_execution.executed_load_observation
                WHERE anatomical_structure_id=? AND channel='DYN_EXU'
                """, java.math.BigDecimal.class, structureId)).isEqualByComparingTo("10.000000");
        assertThat(jdbc.queryForList("""
                SELECT channel || ':' || side FROM training_execution.executed_load_observation
                ORDER BY channel
                """, String.class)).containsExactly(
                        "DYN_EXU:LEFT", "IMPACT_CONTACTS:BILATERAL", "ISO_SEC:RIGHT");
        assertThat(jdbc.queryForObject("""
                SELECT value_high FROM training_execution.executed_load_observation
                WHERE channel='ISO_SEC'
                """, java.math.BigDecimal.class)).isEqualByComparingTo("30.000000");
        assertThat(jdbc.queryForObject(
                "SELECT target_sets * target_repetitions FROM training_planning.exercise_prescription",
                Integer.class)).isEqualTo(24);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.executed_load_aggregate", Long.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.execution_qualification", Long.class)).isEqualTo(1);

        String correction = """
                {"reason":"Device upload corrected the repetitions",
                 "exercisePrescriptionId":"%s","correctedSets":1,"correctedRepetitions":4,
                 "correctedSide":"RIGHT","observationMode":"DEVICE"}
                """.formatted(prescriptionId);
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post("/api/v1/session-executions/{id}/corrections", executionId)
                            .with(role("participant", "PARTICIPANT"))
                            .header("Idempotency-Key", "device-correction")
                            .contentType("application/json").content(correction))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/specialist/participants/{id}/executions", participantId)
                        .with(role("specialist", "SPECIALIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].corrections[0].correctedSets").value(1))
                .andExpect(jsonPath("$[0].corrections[0].observationMode").value("DEVICE"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.execution_correction", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT value_high FROM training_execution.executed_load_observation WHERE channel='DYN_EXU'",
                java.math.BigDecimal.class)).isEqualByComparingTo("4.000000");
        assertThat(jdbc.queryForObject(
                "SELECT side FROM training_execution.executed_load_observation WHERE channel='DYN_EXU'",
                String.class)).isEqualTo("RIGHT");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM training_execution.execution_qualification", String.class)).isEqualTo("REVERSED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit.outbox_event
                WHERE event_type='ExecutionQualificationReversed'
                """, Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT payload FROM audit.outbox_event", String.class))
                .noneMatch(payload -> payload.toLowerCase().matches(".*(pain|difficulty|note|clinical|restriction).*"));
    }

    @Test
    void skippedLegacyDeclarationIsTerminalButDoesNotQualifyOrCompleteThePlannedSession() throws Exception {
        createPlan();
        UUID sessionId = sessionId();
        UUID prescriptionId = prescriptionId();
        String request = """
                {"declaredCompletion":true,"painLevel":0,"difficultyLevel":4,"results":[{
                "exercisePrescriptionId":"%s","skipped":true}]}
                """.formatted(prescriptionId);

        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "skipped-terminal")
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.declaredCompletion").value(false));

        UUID executionId = jdbc.queryForObject("SELECT id FROM training_execution.session_execution", UUID.class);
        assertThat(projections.consume(declarationEvent(executionId)).created()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM training_planning.planned_session WHERE id=?", String.class, sessionId))
                .isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_execution.execution_qualification", Long.class)).isZero();
        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", "skipped-terminal-retry")
                        .contentType("application/json").content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void post24hAlertsHaveAuthorizedLifecycleAndIdempotentHistory() throws Exception {
        createPlan();
        declare(sessionId(), prescriptionId(), "alert-execution", 2, 6);
        UUID executionId = jdbc.queryForObject("SELECT id FROM training_execution.session_execution", UUID.class);
        String response = "{\"painLevel\":8,\"difficultyLevel\":9,\"note\":\"Private follow-up\","
                + "\"observationMode\":\"DECLARED\"}";
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post("/api/v1/session-executions/{id}/post-24h-responses", executionId)
                            .with(role("participant", "PARTICIPANT"))
                            .header("Idempotency-Key", "post24-once")
                            .contentType("application/json").content(response))
                    .andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.post_24h_response", Long.class)).isEqualTo(1);
        UUID alertId = jdbc.queryForObject("""
                SELECT id FROM training_execution.execution_alert WHERE alert_type='POST_24H_PAIN'
                """, UUID.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM training_execution.execution_alert
                WHERE alert_type IN ('PAIN_REPORTED', 'POST_24H_PAIN', 'POST_24H_DIFFICULTY')
                """, Long.class)).isEqualTo(3);
        mvc.perform(post("/api/v1/session-executions/{id}/alerts/{alertId}/transitions", executionId, alertId)
                        .with(role("participant", "PARTICIPANT"))
                        .contentType("application/json").content("{\"action\":\"RESOLVE\"}"))
                .andExpect(status().isForbidden());
        transition(executionId, alertId, "participant", "PARTICIPANT", "ACKNOWLEDGE", null, 200);
        transition(executionId, alertId, "specialist", "SPECIALIST", "RESOLVE", null, 200);
        transition(executionId, alertId, "specialist", "SPECIALIST", "REOPEN", null, 200);
        transition(executionId, alertId, "specialist", "SPECIALIST", "ASSIGN", specialistId, 200);
        transition(executionId, alertId, "foreign-specialist", "SPECIALIST", "RESOLVE", null, 403);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.execution_alert_history", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT owner_account_id FROM training_execution.execution_alert WHERE id=?",
                UUID.class, alertId)).isEqualTo(specialistId);
    }

    @Test
    void failedProjectionRemainsAuditableWhenPinnedVersionIsWithdrawn() throws Exception {
        executionContribution();
        createPlan();
        declare(sessionId(), prescriptionId(), "recover-execution", 0, 4);
        UUID executionId = jdbc.queryForObject("SELECT id FROM training_execution.session_execution", UUID.class);
        jdbc.update("UPDATE exercise_catalog.exercise_version SET status='WITHDRAWN', withdrawn_at=now() WHERE id=?",
                exerciseVersionId);
        assertThat(projections.recover(Instant.now().plusSeconds(60)).failed()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT projection_status FROM training_execution.session_execution", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_execution.execution_projection_receipt", Long.class)).isZero();
        assertThat(executionId).isNotNull();
    }

    @Test
    void openApiPublishesPlanningAndExecutionContracts() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/training-plans']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/training-plans'].post.deprecated").value(true))
                .andExpect(jsonPath("$.paths['/api/v2/training-plans']").exists())
                .andExpect(jsonPath("$.paths['/api/v2/training-plans/revisions/{revisionId}/goals']").exists())
                .andExpect(jsonPath("$.paths['/api/v2/training-plans/revisions/{revisionId}/prescriptions']")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v2/training-plans/revisions/{revisionId}/sessions']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AddGoalCommand.properties.participantGoalId").exists())
                .andExpect(jsonPath("$.components.schemas.AddSessionCommand.properties.exerciseSetVersionId").exists())
                .andExpect(jsonPath("$.paths['/api/v2/training-plans/revisions/{revisionId}/structural-validation']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/training-plans/revisions/{revisionId}/load-preview']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/planned-sessions/{sessionId}/executions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/session-executions/{executionId}/corrections']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/specialist/participants/{participantId}/executions']")
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

    private void participantRecord(UUID accountId) {
        jdbc.update("""
                INSERT INTO participant.participant_record
                    (id, display_name, record_status, relationship_context, created_by_specialist_id, created_at, updated_at, version)
                VALUES (?, 'Execution participant', 'ACTIVE', 'CLIENT', ?, now(), now(), 0)
                """, accountId, specialistId);
        jdbc.update("""
                INSERT INTO participant.participant_access_link
                    (id, participant_id, principal_account_id, access_status, linked_at, activated_at, version)
                VALUES (?, ?, ?, 'ACTIVE', now(), now(), 0)
                """, UUID.randomUUID(), accountId, accountId);
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
                VALUES (?, ?, 1, 'APPROVED', 'Perform a controlled supported squat.', 'SQUAT',
                        'STRENGTH', 'MODERATE', 'FOUNDATIONAL', 'ANY', now(), NULL, 0)
                """, versionId, exerciseId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version_contraindication
                    (exercise_version_id, contraindication_tag)
                VALUES (?, 'ACUTE_KNEE_PAIN')
                """, versionId);
        executionStructureId = addExecutionContributions(versionId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_review
                    (id, exercise_version_id, review_area, decision, reviewer_subject, reviewed_at)
                SELECT gen_random_uuid(), ?, area, 'APPROVED', 'execution-fixture-reviewer', now()
                FROM unnest(ARRAY['CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE']) area
                """, versionId);
        jdbc.update("""
                UPDATE exercise_catalog.exercise_version
                SET status = 'PUBLISHED', published_at = now()
                WHERE id = ?
                """, versionId);
        return versionId;
    }

    private UUID executionContribution() {
        return executionStructureId;
    }

    private P4Fixture p4Fixture() { return p4Fixture(false); }

    private P4Fixture p4Fixture(boolean durationOnly) {
        UUID accountId = UUID.randomUUID();
        UUID canonicalParticipantId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID microcycleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            nativeUpdate("""
                    INSERT INTO identity_access.principal_account (id, external_subject, status, profile_type, created_at, version)
                    VALUES (:accountId, :subject, 'ACTIVE', 'PARTICIPANT', now(), 0)
                    """, "accountId", accountId, "subject", p4Subject = "p4-participant-" + accountId);
            nativeUpdate("""
                    INSERT INTO participant.participant_record (id, display_name, record_status, relationship_context, time_zone_id, created_by_specialist_id, created_at, updated_at, version)
                    VALUES (:participantId, 'P4 participant', 'ACTIVE', 'CLIENT', 'UTC', :specialistId, now(), now(), 0)
                    """, "participantId", canonicalParticipantId, "specialistId", specialistId);
            nativeUpdate("""
                    INSERT INTO participant.participant_access_link (id, participant_id, principal_account_id, access_status, linked_at, activated_at, version)
                    VALUES (:id, :participantId, :accountId, 'ACTIVE', now(), now(), 0)
                    """, "id", UUID.randomUUID(), "participantId", canonicalParticipantId, "accountId", accountId);
            nativeUpdate("""
                    INSERT INTO specialist.participant_specialist_relationship (id, specialist_account_id, participant_id, status, activated_at)
                    VALUES (:id, :specialistId, :participantId, 'ACTIVE', now())
                    """, "id", UUID.randomUUID(), "specialistId", specialistId, "participantId", canonicalParticipantId);
            nativeUpdate("""
                    INSERT INTO exercise_catalog.exercise (id, canonical_name, created_at, created_by_subject)
                    VALUES (:exerciseId, 'P4 controlled squat', now(), 'p4-fixture')
                    """, "exerciseId", exerciseId);
            nativeUpdate("""
                    INSERT INTO exercise_catalog.exercise_version (id, exercise_id, version_number, status, instruction, movement_pattern,
                        stimulus_type, fatigue_profile, technical_level, environment, created_at, published_at, version)
                    VALUES (:versionId, :exerciseId, 1, 'APPROVED', 'P4 controlled squat', 'SQUAT',
                        'STRENGTH', 'MODERATE', 'FOUNDATIONAL', 'ANY', now(), NULL, 0)
                    """, "versionId", versionId, "exerciseId", exerciseId);
            nativeUpdate("""
                    INSERT INTO anatomy_reference.anatomical_structure (id, code, type, display_name, side_policy, status, taxonomy_version,
                        created_by_subject, created_at, published_at, version)
                    VALUES (:id, :code, 'JOINT', 'P4 knee', 'LEFT_RIGHT', 'PUBLISHED', 1, 'p4-fixture', now(), now(), 0)
                    """, "id", UUID.randomUUID(), "code", "P4_KNEE_" + versionId);
            nativeUpdate("""
                    INSERT INTO exercise_catalog.exercise_contribution (id, exercise_version_id, anatomical_structure_id, contribution_role,
                        load_channel, contribution_band, coefficient_low, coefficient_high, confidence_class, evidence_grade,
                        calculation_role, side_rule, created_at, created_by_subject)
                    SELECT :id, :versionId, id, 'PRIMARY', 'DYN_EXU', 'HIGH', 1.0, 1.0, 'TEST', 'TEST',
                        'ALLOCATION', 'AS_PRESCRIBED', now(), 'p4-fixture'
                    FROM anatomy_reference.anatomical_structure WHERE code=:code
                    """, "id", UUID.randomUUID(), "versionId", versionId, "code", "P4_KNEE_" + versionId);
            nativeUpdate("""
                    INSERT INTO exercise_catalog.exercise_review (id, exercise_version_id, review_area, decision, reviewer_subject, reviewed_at)
                    SELECT gen_random_uuid(), :versionId, area, 'APPROVED', 'p4-fixture-reviewer', now()
                    FROM unnest(ARRAY['CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE']) area
                    """, "versionId", versionId);
            nativeUpdate("UPDATE exercise_catalog.exercise_version SET status='PUBLISHED', published_at=now() WHERE id=:versionId",
                    "versionId", versionId);
            nativeUpdate("""
                    INSERT INTO training_planning.training_goal (id, participant_id, name, created_by_account_id, created_at, perspective, category, title, priority, status)
                    VALUES (:goalId, :participantId, 'P4 goal', :specialistId, now(), 'GENERAL_FITNESS', 'LEGACY', 'P4 goal', 50, 'ACTIVE')
                    """, "goalId", goalId, "participantId", canonicalParticipantId, "specialistId", specialistId);
            nativeUpdate("""
                    INSERT INTO training_planning.training_plan (id, goal_id, participant_id, created_by_account_id, name, plan_mode, status, created_at, purpose, owner_account_id, version)
                    VALUES (:planId, :goalId, :participantId, :specialistId, 'P4 plan', 'SPECIALIST_ASSIGNED', 'ACTIVE', now(), 'P4 fixture', :specialistId, 0)
                    """, "planId", planId, "goalId", goalId, "participantId", canonicalParticipantId, "specialistId", specialistId);
            nativeUpdate("""
                    INSERT INTO training_planning.plan_revision (id, plan_id, revision_number, status, phase_intent, author_account_id,
                        author_capability, migration_origin, assessment_status, draft_updated_at, created_at, version)
                    VALUES (:revisionId, :planId, 1, 'ACTIVE', 'P4 fixture', :specialistId, 'LEGACY_AUTHOR',
                        'LEGACY_V1', 'NOT_ASSESSED', now(), now(), 0)
                    """, "revisionId", revisionId, "planId", planId, "specialistId", specialistId);
            nativeUpdate("UPDATE training_planning.training_plan SET current_revision_id=:revisionId WHERE id=:planId",
                    "revisionId", revisionId, "planId", planId);
            nativeUpdate("UPDATE training_planning.training_goal SET revision_id=:revisionId WHERE id=:goalId",
                    "revisionId", revisionId, "goalId", goalId);
            nativeUpdate("""
                    INSERT INTO training_planning.training_cycle (id, plan_id, revision_id, sequence_number, name, phase_intent)
                    VALUES (:cycleId, :planId, :revisionId, 1, 'P4 cycle', 'P4 fixture')
                    """, "cycleId", cycleId, "planId", planId, "revisionId", revisionId);
            nativeUpdate("""
                    INSERT INTO training_planning.microcycle (id, cycle_id, sequence_number, name, phase_intent)
                    VALUES (:microcycleId, :cycleId, 1, 'P4 microcycle', 'P4 fixture')
                    """, "microcycleId", microcycleId, "cycleId", cycleId);
            nativeUpdate("""
                    INSERT INTO training_planning.planned_session (id, microcycle_id, participant_id, title, scheduled_date, session_kind, status, assigned_at, creation_source)
                    VALUES (:sessionId, :microcycleId, :participantId, 'P4 session', CURRENT_DATE, 'SELF_GUIDED', 'ASSIGNED', now(), 'LEGACY_V1')
                    """, "sessionId", sessionId, "microcycleId", microcycleId, "participantId", canonicalParticipantId);
            nativeUpdate("""
                    INSERT INTO training_planning.exercise_prescription (id, planned_session_id, exercise_version_id, position, target_sets, target_repetitions, target_load_kg, notes)
                    VALUES (:prescriptionId, :sessionId, :versionId, 1, 3, :repetitions, 0, 'P4 fixture')
                    """, "prescriptionId", prescriptionId, "sessionId", sessionId, "versionId", versionId,
                    "repetitions", durationOnly ? null : 10);
            if (durationOnly) nativeUpdate("UPDATE training_planning.exercise_prescription SET target_duration_seconds=30 WHERE id=:id", "id", prescriptionId);
            nativeUpdate("""
                    INSERT INTO safety.plan_safety_assessment (id, participant_account_id, participant_id, revision_id, load_snapshot_id,
                        load_input_checksum, load_calculation_version, ruleset_code, ruleset_version, result, restriction_snapshot,
                        ruleset_snapshot, load_snapshot, assessed_at)
                    VALUES (:id, :accountId, :participantId, :revisionId, :loadSnapshotId, 'p4', 'P4', 'SAFETY_V2', 1, 'PASS', '', 'P4', 'P4', now())
                    """, "id", UUID.randomUUID(), "accountId", accountId, "participantId", canonicalParticipantId,
                    "revisionId", revisionId, "loadSnapshotId", UUID.randomUUID());
        });
        return new P4Fixture(accountId, canonicalParticipantId, revisionId, sessionId, prescriptionId);
    }

    private UUID startP4Attempt(P4Fixture fixture, String key) throws Exception {
        String response = startP4(fixture, key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("attemptId").asText());
    }

    private P4Fixture activateSecondP4Plan(P4Fixture first) {
        UUID planId = UUID.randomUUID(); UUID revisionId = UUID.randomUUID(); UUID cycleId = UUID.randomUUID();
        UUID microcycleId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID(); UUID goalId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            nativeUpdate("""
                    INSERT INTO training_planning.training_goal (id, participant_id, name, created_by_account_id, created_at, perspective, category, title, priority, status)
                    VALUES (:goalId, :participantId, 'P4 second goal', :specialistId, now(), 'GENERAL_FITNESS', 'LEGACY', 'P4 second goal', 50, 'ACTIVE')
                    """, "goalId", goalId, "participantId", first.participantId(), "specialistId", specialistId);
            nativeUpdate("""
                    INSERT INTO training_planning.training_plan (id, goal_id, participant_id, created_by_account_id, name, plan_mode, status, created_at, purpose, owner_account_id, version)
                    VALUES (:planId, :goalId, :participantId, :accountId, 'P4 second plan', 'SELF_DIRECTED', 'ACTIVE', now(), 'P4 fixture', :accountId, 0)
                    """, "planId", planId, "goalId", goalId, "participantId", first.participantId(), "accountId", first.accountId());
            nativeUpdate("""
                    INSERT INTO training_planning.plan_revision (id, plan_id, revision_number, status, phase_intent, author_account_id, author_capability, migration_origin, assessment_status, draft_updated_at, created_at, version)
                    VALUES (:revisionId, :planId, 1, 'ACTIVE', 'P4 fixture', :accountId, 'SELF_AUTHOR', 'LEGACY_V1', 'NOT_ASSESSED', now(), now(), 0)
                    """, "revisionId", revisionId, "planId", planId, "accountId", first.accountId());
            nativeUpdate("UPDATE training_planning.training_plan SET current_revision_id=:revisionId WHERE id=:planId", "revisionId", revisionId, "planId", planId);
            nativeUpdate("UPDATE training_planning.training_goal SET revision_id=:revisionId WHERE id=:goalId", "revisionId", revisionId, "goalId", goalId);
            nativeUpdate("""
                    INSERT INTO training_planning.training_cycle (id, plan_id, revision_id, sequence_number, name, phase_intent)
                    VALUES (:cycleId, :planId, :revisionId, 1, 'P4 second cycle', 'P4 fixture')
                    """, "cycleId", cycleId, "planId", planId, "revisionId", revisionId);
            nativeUpdate("""
                    INSERT INTO training_planning.microcycle (id, cycle_id, sequence_number, name, phase_intent)
                    VALUES (:microcycleId, :cycleId, 1, 'P4 second microcycle', 'P4 fixture')
                    """, "microcycleId", microcycleId, "cycleId", cycleId);
            nativeUpdate("""
                    INSERT INTO training_planning.planned_session (id, microcycle_id, participant_id, title, scheduled_date, session_kind, status, assigned_at, creation_source)
                    VALUES (:sessionId, :microcycleId, :participantId, 'P4 second session', CURRENT_DATE, 'SELF_GUIDED', 'ASSIGNED', now(), 'LEGACY_V1')
                    """, "sessionId", sessionId, "microcycleId", microcycleId, "participantId", first.participantId());
            nativeUpdate("""
                    INSERT INTO safety.plan_safety_assessment (id, participant_account_id, participant_id, revision_id, load_snapshot_id, load_input_checksum, load_calculation_version, ruleset_code, ruleset_version, result, restriction_snapshot, ruleset_snapshot, load_snapshot, assessed_at)
                    VALUES (:id, :accountId, :participantId, :revisionId, :loadSnapshotId, 'p4', 'P4', 'SAFETY_V2', 1, 'PASS', '', 'P4', 'P4', now())
                    """, "id", UUID.randomUUID(), "accountId", first.accountId(), "participantId", first.participantId(), "revisionId", revisionId, "loadSnapshotId", UUID.randomUUID());
        });
        return new P4Fixture(first.accountId(), first.participantId(), revisionId, sessionId, null);
    }

    private org.springframework.test.web.servlet.ResultActions startP4(P4Fixture fixture, String key) throws Exception {
        return mvc.perform(post("/api/v1/participant/session-attempts").with(role(p4Subject, "PARTICIPANT"))
                .header("Idempotency-Key", key).contentType("application/json")
                .content("{\"plannedSessionId\":\"" + fixture.sessionId() + "\",\"planRevisionId\":\""
                        + fixture.revisionId() + "\",\"selectedVariantType\":\"STANDARD\"}"));
    }

    private void recordP4Fact(UUID attemptId, UUID prescriptionId, String outcome, String reason, String resultFields) throws Exception {
        mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/facts", attemptId)
                        .with(role(p4Subject, "PARTICIPANT")).contentType("application/json")
                        .content(fact(prescriptionId, outcome, reason, resultFields)))
                .andExpect(status().isOk());
    }

    private static String fact(UUID prescriptionId, String outcome, String reason, String resultFields) {
        String suffix = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return "{\"exercisePrescriptionId\":\"" + prescriptionId + "\",\"outcome\":\"" + outcome + "\"" + suffix
                + ",\"result\":{\"exercisePrescriptionId\":\"" + prescriptionId + "\"," + resultFields.substring(1) + "}";
    }

    private org.springframework.test.web.servlet.ResultActions finish(UUID attemptId, String key, String intent, String stopReason) throws Exception {
        String reason = stopReason == null ? "" : ",\"stopReason\":\"" + stopReason + "\"";
        return mvc.perform(post("/api/v1/participant/session-attempts/{attemptId}/finish", attemptId)
                .with(role(p4Subject, "PARTICIPANT")).header("Idempotency-Key", key).contentType("application/json")
                .content("{\"intent\":\"" + intent + "\",\"painLevel\":0,\"difficultyLevel\":4" + reason + "}"));
    }

    private void nativeUpdate(String sql, Object... bindings) {
        var query = entityManager.createNativeQuery(sql);
        for (int index = 0; index < bindings.length; index += 2) {
            query.setParameter((String) bindings[index], bindings[index + 1]);
        }
        query.executeUpdate();
    }

    private OutboxMessage declarationEventNative(UUID executionId) {
        return transactionTemplate.execute(status -> {
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                    SELECT id, aggregate_type, aggregate_id, event_type, payload, occurred_at
                    FROM audit.outbox_event WHERE aggregate_id=:executionId AND event_type='SessionExecutionDeclared'
                    """).setParameter("executionId", executionId).getSingleResult();
            return new OutboxMessage((UUID) row[0], (String) row[1], (UUID) row[2], (String) row[3],
                    (String) row[4], (Instant) row[5]);
        });
    }

    private record P4Fixture(UUID accountId, UUID participantId, UUID revisionId, UUID sessionId, UUID prescriptionId) { }

    private UUID addExecutionContributions(UUID versionId) {
        UUID structureId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO anatomy_reference.anatomical_structure
                    (id, code, type, display_name, side_policy, status, taxonomy_version,
                     created_by_subject, created_at, published_at, version)
                VALUES (?, ?, 'JOINT', 'Test knee', 'LEFT_RIGHT', 'PUBLISHED', 1,
                        'test', now(), now(), 0)
                """, structureId, "TEST_KNEE_" + structureId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_contribution
                    (id, exercise_version_id, anatomical_structure_id, contribution_role,
                     load_channel, contribution_band, coefficient_low, coefficient_high,
                     confidence_class, evidence_grade, calculation_role, side_rule,
                     created_at, created_by_subject)
                VALUES (?, ?, ?, 'PRIMARY', 'DYN_EXU', 'HIGH', 0.5, 1.0,
                        'TEST', 'TEST', 'ALLOCATION', 'AS_PRESCRIBED', now(), 'test')
                """, UUID.randomUUID(), versionId, structureId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_contribution
                    (id, exercise_version_id, anatomical_structure_id, contribution_role,
                     load_channel, contribution_band, coefficient_low, coefficient_high,
                     confidence_class, evidence_grade, calculation_role, side_rule,
                     created_at, created_by_subject)
                VALUES (?, ?, ?, 'PRIMARY', 'ISO_SEC', 'MODERATE', 0.25, 0.5,
                        'TEST', 'TEST', 'ALLOCATION', 'RIGHT', now(), 'test')
                """, UUID.randomUUID(), versionId, structureId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_contribution
                    (id, exercise_version_id, anatomical_structure_id, contribution_role,
                     load_channel, contribution_band, coefficient_low, coefficient_high,
                     confidence_class, evidence_grade, calculation_role, side_rule,
                     created_at, created_by_subject)
                VALUES (?, ?, ?, 'PRIMARY', 'IMPACT_CONTACTS', 'MODERATE', 0.1, 0.2,
                        'TEST', 'TEST', 'ALLOCATION', 'BILATERAL', now(), 'test')
                """, UUID.randomUUID(), versionId, structureId);
        return structureId;
    }

    private OutboxMessage declarationEvent(UUID executionId) {
        return jdbc.queryForObject("""
                SELECT id, aggregate_type, aggregate_id, event_type, payload, occurred_at
                FROM audit.outbox_event
                WHERE aggregate_id=? AND event_type='SessionExecutionDeclared'
                """, (rs, row) -> new OutboxMessage(rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"), rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"), rs.getString("payload"),
                rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant()), executionId);
    }

    private void declare(UUID sessionId, UUID prescriptionId, String key, int pain, int difficulty)
            throws Exception {
        mvc.perform(post("/api/v1/planned-sessions/{id}/executions", sessionId)
                        .with(role("participant", "PARTICIPANT"))
                        .header("Idempotency-Key", key).contentType("application/json")
                        .content(executionRequest(prescriptionId, true, pain, difficulty)))
                .andExpect(status().isOk());
    }

    private void transition(UUID executionId, UUID alertId, String subject, String role,
                            String action, UUID assignedOwner, int expectedStatus) throws Exception {
        String owner = assignedOwner == null ? "" : ",\"ownerAccountId\":\"" + assignedOwner + "\"";
        mvc.perform(post("/api/v1/session-executions/{id}/alerts/{alertId}/transitions", executionId, alertId)
                        .with(role(subject, role)).contentType("application/json")
                        .content("{\"action\":\"" + action + "\"" + owner
                                + ",\"commentReference\":\"ticket:test\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private void createPlan() throws Exception {
        UUID goalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID microcycleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_planning.training_goal
                    (id, participant_id, name, created_by_account_id, created_at,
                     perspective, category, title, priority, status)
                VALUES (?, ?, 'Legacy execution goal', ?, now(),
                        'GENERAL_FITNESS', 'LEGACY', 'Legacy execution goal', 50, 'ACTIVE')
                """, goalId, participantId, specialistId);
        jdbc.update("""
                INSERT INTO training_planning.training_plan
                    (id, goal_id, participant_id, created_by_account_id, name,
                     plan_mode, status, created_at, purpose, owner_account_id, version)
                VALUES (?, ?, ?, ?, 'Legacy execution fixture',
                        'SPECIALIST_ASSIGNED', 'ACTIVE', now(), 'Legacy execution fixture', ?, 0)
                """, planId, goalId, participantId, specialistId, specialistId);
        jdbc.update("""
                INSERT INTO training_planning.plan_revision
                    (id, plan_id, revision_number, status, phase_intent, author_account_id,
                     author_capability, migration_origin, assessment_status,
                     draft_updated_at, created_at, version)
                VALUES (?, ?, 1, 'ACTIVE', 'Legacy execution fixture', ?, 'LEGACY_AUTHOR',
                        'LEGACY_V1', 'NOT_ASSESSED', now(), now(), 0)
                """, revisionId, planId, specialistId);
        jdbc.update("UPDATE training_planning.training_plan SET current_revision_id = ? WHERE id = ?",
                revisionId, planId);
        jdbc.update("UPDATE training_planning.training_goal SET revision_id = ? WHERE id = ?",
                revisionId, goalId);
        jdbc.update("""
                INSERT INTO training_planning.training_cycle
                    (id, plan_id, revision_id, sequence_number, name, phase_intent)
                VALUES (?, ?, ?, 1, 'Legacy execution cycle', 'Legacy fixture')
                """, cycleId, planId, revisionId);
        jdbc.update("""
                INSERT INTO training_planning.microcycle
                    (id, cycle_id, sequence_number, name, phase_intent)
                VALUES (?, ?, 1, 'Legacy execution microcycle', 'Legacy fixture')
                """, microcycleId, cycleId);
        jdbc.update("""
                INSERT INTO training_planning.planned_session
                    (id, microcycle_id, participant_id, title, session_kind,
                     status, assigned_at, creation_source)
                VALUES (?, ?, ?, 'Supported strength', 'SELF_GUIDED',
                        'ASSIGNED', now(), 'LEGACY_V1')
                """, sessionId, microcycleId, participantId);
        jdbc.update("""
                INSERT INTO training_planning.exercise_prescription
                    (id, planned_session_id, exercise_version_id, position,
                     target_sets, target_repetitions, target_load_kg, notes)
                VALUES (?, ?, ?, 1, 3, 8, 0, 'Legacy execution fixture')
                """, UUID.randomUUID(), sessionId, exerciseVersionId);
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
