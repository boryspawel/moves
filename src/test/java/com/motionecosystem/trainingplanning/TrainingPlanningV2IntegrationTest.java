package com.motionecosystem.trainingplanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.application.workspace.SpecialistParticipantReadService;
import com.motionecosystem.application.workspace.SpecialistClientService;
import com.motionecosystem.adherence.TodayAgendaService;
import com.motionecosystem.consent.ConsentGrantService;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.consent.api.TestDefaultConsentOverridePort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.support.PostgresTestConfiguration;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.BudgetAction;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.GoalPerspective;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanMode;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddCycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddGoalCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddLoadBudgetCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddMicrocycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddSessionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateDraftCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateRevisionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.DefineSessionVariantCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.DeleteGoalCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.EditorView;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.VariantItemCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.ValidateCommand;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.ActivateWorkflowCommand;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.ValidateWorkflowCommand;
import com.motionecosystem.exercisesets.application.ExerciseSetApplicationService;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = MotionEcosystemApplication.class, properties = "moves.test-default-consent.enabled=true")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class TrainingPlanningV2IntegrationTest {

    @Autowired TrainingPlanningV2Service planning;
    @Autowired ConsentGrantService consents;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExerciseSetApplicationService exerciseSets;
    @Autowired SpecialistPlanFacadeService specialistPlans;
    @Autowired PlanRevisionWorkflowService workflow;
    @Autowired TodayAgendaService today;
    @Autowired SpecialistParticipantReadService timeline;
    @Autowired SpecialistClientService clients;
    @Autowired TestDefaultConsentOverridePort testConsentOverrides;

    UUID participantId;
    UUID otherParticipantId;
    UUID specialistId;
    UUID foreignSpecialistId;
    UUID exerciseVersionId;
    UUID exerciseSetVersionId;

    @BeforeEach
    void setUp() {
        participantId = account("planning-participant", "PARTICIPANT");
        otherParticipantId = account("other-planning-participant", "PARTICIPANT");
        specialistId = account("planning-specialist", "SPECIALIST");
        foreignSpecialistId = account("foreign-planning-specialist", "SPECIALIST");
        participantRecord(participantId);
        participantRecord(otherParticipantId);
        relationship(specialistId, participantId);
        specialistProfile(specialistId);
        scope(specialistId, "TRAINER");
        UUID template = consents.publishTemplate(
                "PLANNING_TEST", 1, "urn:test:planning", "EXPLICIT_CONSENT").id();
        consents.grant("planning-participant", new ConsentGrantService.GrantCommand(
                specialistId, ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING, template,
                java.util.Set.of(ConsentDecisionPort.DataScope.PLAN), null, null));
        exerciseVersionId = publishedExerciseVersion();
        exerciseSetVersionId = publishedSetVersion();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.audit_event,
                    consent.consent_template_version,
                    training_planning.training_plan,
                    training_planning.training_goal,
                    specialist.participant_specialist_relationship,
                    specialist.professional_scope,
                    participant.participant_access_link,
                    participant.participant_record,
                    exercise_catalog.exercise,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void buildsDraftIncrementallyWithTypedDoseOptimisticLockAndStructuralValidation() {
        EditorView editor = specialistDraft();
        UUID revisionId = editor.revision().revisionId();
        assertThat(editor.planStatus()).isEqualTo("DRAFT");
        assertThat(editor.revision().status()).isEqualTo("DRAFT");
        assertThat(editor.revision().assessmentStatus()).isEqualTo("NOT_ASSESSED");

        editor = planning.addGoal("planning-specialist", revisionId,
                new AddGoalCommand(version(editor), canonicalGoal(participantId)));
        long currentVersion = version(editor);

        assertStatus(HttpStatus.CONFLICT, () -> planning.addCycle("planning-specialist", revisionId,
                cycle(0, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))));
        assertStatus(HttpStatus.BAD_REQUEST, () -> planning.addCycle("planning-specialist", revisionId,
                cycle(currentVersion, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1))));

        editor = planning.addCycle("planning-specialist", revisionId,
                cycle(currentVersion, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        UUID cycleId = editor.revision().cycles().getFirst().id();
        editor = planning.addMicrocycle("planning-specialist", revisionId, new AddMicrocycleCommand(
                version(editor), cycleId, 1, "Week one", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7), "Acclimation", "Complete all sessions"));
        UUID microcycleId = editor.revision().cycles().getFirst().microcycles().getFirst().id();
        editor = planning.addSession("planning-specialist", revisionId, new AddSessionCommand(
                version(editor), microcycleId, "MVP modalities", LocalDate.of(2026, 8, 2),
                Instant.parse("2026-08-02T06:00:00Z"), Instant.parse("2026-08-02T20:00:00Z"), 60, exerciseSetVersionId));
        UUID sessionId = editor.revision().cycles().getFirst().microcycles().getFirst().sessions().getFirst().id();

        assertThat(editor.revision().cycles().getFirst().microcycles().getFirst().sessions().getFirst()
                .sourceExerciseSetVersionId()).isEqualTo(exerciseSetVersionId);
        assertThat(editor.revision().cycles().getFirst().microcycles().getFirst().sessions().getFirst()
                .sourceSnapshot()).contains("exerciseSetVersionId");
        assertThat(editor.revision().cycles().getFirst().microcycles().getFirst().sessions().getFirst()
                .prescriptions()).singleElement().satisfies(item -> {
                    assertThat(item.exerciseVersionId()).isEqualTo(exerciseVersionId);
                    assertThat(item.canonicalDoseType()).isEqualTo("STRENGTH");
                    assertThat(item.materializedSnapshot()).contains("STRENGTH");
                });
        UUID prescriptionId = editor.revision().cycles().getFirst().microcycles().getFirst().sessions().getFirst()
                .prescriptions().getFirst().id();
        EditorView beforeVariant = editor;
        editor = planning.defineSessionVariant("planning-specialist", revisionId,
                new DefineSessionVariantCommand(version(beforeVariant), sessionId,
                        TrainingPlanningModel.SessionVariantType.SHORT, 30,
                        List.of(new VariantItemCommand(prescriptionId, 1))));

        editor = planning.addLoadBudget("planning-specialist", revisionId, new AddLoadBudgetCommand(
                version(editor), "DYN_EXU", BigDecimal.valueOf(100), BigDecimal.valueOf(180),
                "EXU", BudgetAction.WARNING));
        var validation = planning.validateStructurally("planning-specialist", revisionId,
                new ValidateCommand(version(editor)));
        assertThat(validation.result()).isEqualTo(TrainingPlanningModel.ValidationResult.PASS);
        assertThat(validation.inputChecksum()).hasSize(64);
        assertThat(planning.editor("planning-specialist", revisionId).revision().status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("""
                SELECT assessment_status FROM training_planning.plan_revision WHERE id = ?
                """, String.class, revisionId)).isEqualTo("NOT_ASSESSED");

        assertThat(Arrays.stream(BudgetAction.values()).map(Enum::name))
                .containsExactly("INFO", "WARNING");
        assertThat(Arrays.stream(AddSessionCommand.class.getRecordComponents()).map(item -> item.getName()))
                .doesNotContain("kind", "sessionKind", "appointment");
        assertThat(jdbc.queryForObject("""
                SELECT session_kind FROM training_planning.planned_session WHERE id = ?
                """, String.class, sessionId)).isEqualTo("SELF_GUIDED");
    }

    @Test
    void enforcesSelfDirectedSpecialistAndResourceOwnership() {
        assertStatus(HttpStatus.GONE, () -> planning.createDraft("planning-participant", new CreateDraftCommand(
                null, "My plan", "Independent training", null, "Build consistency",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))));

        assertStatus(HttpStatus.GONE, () -> planning.createDraft("planning-participant",
                new CreateDraftCommand(otherParticipantId, "Wrong", "Wrong owner", PlanMode.SELF_DIRECTED,
                        "No access", null, null)));
        EditorView assigned = specialistDraft();
        assertThat(assigned.mode()).isEqualTo("SPECIALIST");
        assertThat(assigned.ownerAccountId()).isEqualTo(specialistId);
        assertStatus(HttpStatus.FORBIDDEN, () -> planning.editor(
                "foreign-planning-specialist", assigned.revision().revisionId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> planning.addGoal("planning-participant",
                assigned.revision().revisionId(), new AddGoalCommand(version(assigned), canonicalGoal(participantId))));
    }

    @Test
    void rejectsGoalFromAnotherParticipantAndKeepsGoalSnapshotFrozen() {
        EditorView editor = specialistDraft();
        UUID goal = canonicalGoal(participantId);
        editor = planning.addGoal("planning-specialist", editor.revision().revisionId(),
                new AddGoalCommand(version(editor), goal));
        String frozenTitle = editor.revision().goals().getFirst().title();
        jdbc.update("UPDATE participant_goals.participant_goal SET title = 'Changed later', version = 1 WHERE id = ?", goal);
        assertThat(planning.editor("planning-specialist", editor.revision().revisionId()).revision()
                .goals().getFirst().title()).isEqualTo(frozenTitle);
        editor = planning.deleteGoal("planning-specialist", editor.revision().revisionId(),
                new DeleteGoalCommand(version(editor), editor.revision().goals().getFirst().id()));
        assertThat(editor.revision().goals()).isEmpty();
        UUID foreign = canonicalGoal(otherParticipantId);
        EditorView frozenEditor = editor;
        assertStatus(HttpStatus.FORBIDDEN, () -> planning.addGoal("planning-specialist",
                frozenEditor.revision().revisionId(), new AddGoalCommand(version(frozenEditor), foreign)));
    }

    @Test
    void activeRevisionIsImmutableAndCanBeClonedAsNewDraft() {
        EditorView original = specialistDraft();
        UUID planId = original.planId();
        UUID revisionId = original.revision().revisionId();
        jdbc.update("UPDATE training_planning.plan_revision SET status = 'ACTIVE' WHERE id = ?", revisionId);
        jdbc.update("UPDATE training_planning.training_plan SET status = 'ACTIVE' WHERE id = ?", planId);

        assertStatus(HttpStatus.CONFLICT, () -> planning.addGoal("planning-specialist", revisionId,
                new AddGoalCommand(0, UUID.randomUUID())));
        assertStatus(HttpStatus.CONFLICT, () -> planning.deleteGoal("planning-specialist", revisionId,
                new DeleteGoalCommand(0, UUID.randomUUID())));

        EditorView clone = planning.createRevision("planning-specialist", planId,
                new CreateRevisionCommand(revisionId, new ActingContext(ProfessionalRole.TRAINER)));
        assertThat(clone.revision().revisionNumber()).isEqualTo(2);
        assertThat(clone.revision().status()).isEqualTo("DRAFT");
        assertThat(planning.history("planning-specialist", planId))
                .extracting(TrainingPlanningV2Persistence.RevisionHistoryItem::status)
                .containsExactly("ACTIVE", "DRAFT");
    }

    @Test
    void specialistFacadeActivatesExactSetSnapshotAndFeedsParticipantAndSpecialistReadModels() {
        UUID participantAccountId = account("facade-participant-account", "PARTICIPANT");
        UUID canonicalParticipantId = UUID.randomUUID();
        participantRecord(canonicalParticipantId, participantAccountId, "UTC");
        relationship(specialistId, canonicalParticipantId);
        UUID template = consents.publishTemplate("FACADE_PLANNING", 1, "urn:test:facade", "EXPLICIT_CONSENT").id();
        consents.grant("facade-participant-account", new ConsentGrantService.GrantCommand(specialistId,
                ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING, template,
                java.util.Set.of(ConsentDecisionPort.DataScope.PLAN, ConsentDecisionPort.DataScope.EXECUTION), null, null));
        UUID goal = canonicalGoal(canonicalParticipantId);
        LocalDate scheduled = LocalDate.now();
        EditorView editor = specialistPlans.create("planning-specialist", canonicalParticipantId,
                new SpecialistPlanFacadeService.CreatePlanCommand(canonicalParticipantId, "Facade plan", null, null,
                        scheduled, scheduled.plusDays(7), goal, new ActingContext(ProfessionalRole.TRAINER)));
        UUID revisionId = editor.revision().revisionId();
        editor = specialistPlans.addSession("planning-specialist", canonicalParticipantId, editor.planId(), revisionId,
                new SpecialistPlanFacadeService.SessionCommand(version(editor), "Exact published set", scheduled,
                        null, null, 45, exerciseSetVersionId));
        editor = planning.editor("planning-specialist", revisionId);
        var session = editor.revision().cycles().getFirst().microcycles().getFirst().sessions().getFirst();
        UUID prescriptionId = session.prescriptions().getFirst().id();
        String sourceSnapshot = session.sourceSnapshot();
        editor = specialistPlans.updateSession("planning-specialist", canonicalParticipantId, editor.planId(), revisionId,
                new SpecialistPlanFacadeService.SessionUpdateCommand(version(editor), session.id(), "Rescheduled exact set",
                        scheduled, null, null, 45, null));
        var scheduleOnly = editor.revision().cycles().getFirst().microcycles().getFirst().sessions().getFirst();
        assertThat(scheduleOnly.sourceSnapshot()).isEqualTo(sourceSnapshot);
        assertThat(scheduleOnly.prescriptions().getFirst().id()).isEqualTo(prescriptionId);
        editor = specialistPlans.updatePeriod("planning-specialist", canonicalParticipantId, editor.planId(), revisionId,
                new SpecialistPlanFacadeService.PeriodCommand(version(editor), scheduled, scheduled.plusDays(8)));
        assertThat(editor.revision().validTo()).isEqualTo(scheduled.plusDays(8));
        UUID planId = editor.planId();
        assertStatus(HttpStatus.NOT_FOUND, () -> specialistPlans.editor("planning-specialist", participantId,
                planId, revisionId));
        assertStatus(HttpStatus.FORBIDDEN, () -> specialistPlans.list("foreign-planning-specialist", canonicalParticipantId,
                new ActingContext(ProfessionalRole.TRAINER)));
        var structural = planning.validateStructurally("planning-specialist", revisionId, new ValidateCommand(version(editor)));
        assertThat(structural.result()).isEqualTo(TrainingPlanningModel.ValidationResult.PASS);
        var validated = workflow.validate("planning-specialist", revisionId,
                new ValidateWorkflowCommand(version(editor), new ActingContext(ProfessionalRole.TRAINER)));
        assertThat(validated.status()).isEqualTo("READY");
        workflow.activate("planning-specialist", revisionId, "facade-activation", new ActivateWorkflowCommand(new ActingContext(ProfessionalRole.TRAINER)));
        assertThat(clients.get("planning-specialist", canonicalParticipantId).activePlan())
                .extracting(SpecialistClientService.ClientActivePlanView::revisionId)
                .isEqualTo(revisionId);
        assertThat(today.today("facade-participant-account").sessions()).extracting(TodayAgendaService.AgendaSessionView::sessionId)
                .contains(scheduleOnly.id());
        var view = timeline.timeline("planning-specialist", canonicalParticipantId,
                new SpecialistParticipantReadService.TimelineQuery(scheduled.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        scheduled.plusDays(3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        java.util.Set.of(SpecialistParticipantReadService.TimelineType.SESSION),
                        SpecialistParticipantReadService.Granularity.DETAIL, null, 10));
        assertThat(view.items()).extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::eventId)
                .contains("planned-session:" + scheduleOnly.id());
        long activeVersion = version(editor);
        assertStatus(HttpStatus.CONFLICT, () -> specialistPlans.updateSession("planning-specialist", canonicalParticipantId,
                planId, revisionId, new SpecialistPlanFacadeService.SessionUpdateCommand(activeVersion, scheduleOnly.id(),
                        "Immutable", scheduled, null, null, 45, null)));
    }

    @Test
    void accountFreeManagedParticipantActivatesWithoutLegacyMetricsAndAppearsOnSpecialistTimeline() {
        UUID managedParticipantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO participant.participant_record
                    (id, display_name, record_status, relationship_context, time_zone_id, created_by_specialist_id, created_at, updated_at, version)
                VALUES (?, 'Account free managed client', 'ACTIVE', 'CLIENT', 'UTC', ?, now(), now(), 0)
                """, managedParticipantId, specialistId);
        relationship(specialistId, managedParticipantId);
        testConsentOverrides.create(new TestDefaultConsentOverridePort.CreateCommand(managedParticipantId, specialistId,
                ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING, java.util.Set.of(ConsentDecisionPort.DataScope.PLAN),
                specialistId, Instant.now()));
        UUID goal = canonicalGoal(managedParticipantId);
        LocalDate scheduled = LocalDate.now();
        EditorView editor = specialistPlans.create("planning-specialist", managedParticipantId,
                new SpecialistPlanFacadeService.CreatePlanCommand(managedParticipantId, "Managed plan", null, null,
                        scheduled, scheduled.plusDays(1), goal, new ActingContext(ProfessionalRole.TRAINER)));
        UUID revisionId = editor.revision().revisionId();
        editor = specialistPlans.addSession("planning-specialist", managedParticipantId, editor.planId(), revisionId,
                new SpecialistPlanFacadeService.SessionCommand(version(editor), "Managed session", scheduled,
                        null, null, 30, exerciseSetVersionId));
        assertThat(planning.validateStructurally("planning-specialist", revisionId, new ValidateCommand(version(editor))).result())
                .isEqualTo(TrainingPlanningModel.ValidationResult.PASS);
        workflow.validate("planning-specialist", revisionId,
                new ValidateWorkflowCommand(version(editor), new ActingContext(ProfessionalRole.TRAINER)));
        workflow.activate("planning-specialist", revisionId, "account-free-activation",
                new ActivateWorkflowCommand(new ActingContext(ProfessionalRole.TRAINER)));
        assertThat(planning.editor("planning-specialist", revisionId).revision().status()).isEqualTo("ACTIVE");
        var view = timeline.timeline("planning-specialist", managedParticipantId,
                new SpecialistParticipantReadService.TimelineQuery(scheduled.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        scheduled.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        java.util.Set.of(SpecialistParticipantReadService.TimelineType.SESSION),
                        SpecialistParticipantReadService.Granularity.DETAIL, null, 10));
        assertThat(view.items()).extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::eventType)
                .contains("SESSION_PLANNED");
    }

    private EditorView specialistDraft() {
        return planning.createDraft("planning-specialist", new CreateDraftCommand(participantId,
                "Foundation plan", "Prepare gradual training", PlanMode.SPECIALIST,
                "Foundation phase", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new ActingContext(ProfessionalRole.TRAINER)));
    }


    private static AddCycleCommand cycle(long version, LocalDate start, LocalDate end) {
        return new AddCycleCommand(version, 1, "Foundation cycle", start, end,
                "Build tolerance", "Complete foundation phase");
    }

    private static long version(EditorView editor) {
        return editor.revision().revisionVersion();
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(expected));
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

    private void relationship(UUID specialist, UUID participant) {
        jdbc.update("""
                INSERT INTO specialist.participant_specialist_relationship
                    (id, specialist_account_id, participant_id, status, activated_at)
                VALUES (?, ?, ?, 'ACTIVE', now())
                """, UUID.randomUUID(), specialist, participant);
    }

    private void participantRecord(UUID accountId) {
        participantRecord(accountId, accountId, null);
    }

    private void participantRecord(UUID participantId, UUID accountId, String timeZone) {
        jdbc.update("""
                INSERT INTO participant.participant_record
                    (id, display_name, record_status, relationship_context, created_by_specialist_id,
                     time_zone_id, created_at, updated_at, version)
                VALUES (?, 'Planning participant', 'ACTIVE', 'CLIENT', ?, ?, now(), now(), 0)
                """, participantId, specialistId, timeZone);
        jdbc.update("""
                INSERT INTO participant.participant_access_link
                    (id, participant_id, principal_account_id, access_status, linked_at, activated_at, version)
                VALUES (?, ?, ?, 'ACTIVE', now(), now(), 0)
                """, UUID.randomUUID(), participantId, accountId);
    }

    private void specialistProfile(UUID specialist) {
        jdbc.update("""
                INSERT INTO specialist.specialist_profile
                    (id, account_id, display_name, specialist_kind, time_zone_id, created_at, updated_at, version)
                VALUES (?, ?, 'Planning trainer', 'TRAINER', 'UTC', now(), now(), 0)
                """, UUID.randomUUID(), specialist);
    }

    private void scope(UUID specialist, String type) {
        jdbc.update("""
                INSERT INTO specialist.professional_scope
                    (specialist_account_id, scope_type, verification_status, verified_at, created_at)
                VALUES (?, ?, 'VERIFIED', now(), now())
                """, specialist, type);
    }

    private UUID publishedExerciseVersion() {
        UUID exerciseId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise
                    (id, canonical_name, created_at, created_by_subject)
                VALUES (?, 'Planning squat', now(), 'catalog-admin')
                """, exerciseId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version
                    (id, exercise_id, version_number, status, instruction, movement_pattern,
                     stimulus_type, fatigue_profile, technical_level, environment,
                     created_at, published_at, version)
                VALUES (?, ?, 1, 'APPROVED', 'Perform a controlled squat.', 'SQUAT',
                        'STRENGTH', 'MODERATE', 'FOUNDATIONAL', 'ANY', now(), NULL, 0)
                """, versionId, exerciseId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version_movement_pattern
                    (exercise_version_id, movement_pattern) VALUES (?, 'SQUAT')
                """, versionId);
        addFixtureReviews(versionId);
        jdbc.update("""
                UPDATE exercise_catalog.exercise_version
                SET status = 'PUBLISHED', published_at = now()
                WHERE id = ?
                """, versionId);
        return versionId;
    }

    private UUID canonicalGoal(UUID participant) {
        UUID goal = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO participant_goals.participant_goal
                    (id, participant_id, specialist_account_id, category, title, description, priority,
                     target_date, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'PERFORMANCE', 'Build squat capacity',
                        'Progress controlled lower-body strength', 1, '2026-09-30', 'ACTIVE', now(), now(), 0)
                """, goal, participant, specialistId);
        jdbc.update("""
                INSERT INTO participant_goals.goal_outcome
                    (id, goal_id, metric_code, baseline, target_value, unit, position, created_at)
                VALUES (?, ?, 'SQUAT_REPS', 5, 10, 'repetitions', 0, now())
                """, UUID.randomUUID(), goal);
        return goal;
    }

    private UUID publishedSetVersion() {
        var set = exerciseSets.create("planning-specialist");
        UUID version = set.versions().getFirst().id();
        exerciseSets.updateMetadata("planning-specialist", set.id(), version,
                new ExerciseSetDtos.MetadataRequest(ExerciseSetModel.SetProfile.MAIN_MODULE,
                        "Squat capacity", null, null, List.of(), 0));
        exerciseSets.addItem("planning-specialist", set.id(), version,
                new ExerciseSetDtos.ItemRequest(exerciseVersionId, ExerciseSetModel.Phase.MAIN,
                        new ExerciseSetDtos.StrengthDose(3, 8, null, null, 60, "3-1-1",
                                BigDecimal.valueOf(20), "kg", BigDecimal.valueOf(7), null,
                                ExerciseSetModel.Side.BILATERAL), "Controlled squat", null, 1));
        return exerciseSets.publish("planning-specialist", set.id(), version, 2).id();
    }

    private void addFixtureReviews(UUID versionId) {
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_review
                    (id, exercise_version_id, review_area, decision, reviewer_subject, reviewed_at)
                SELECT gen_random_uuid(), ?, area, 'APPROVED', 'planning-fixture-reviewer', now()
                FROM unnest(ARRAY['CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE']) area
                """, versionId);
    }
}
