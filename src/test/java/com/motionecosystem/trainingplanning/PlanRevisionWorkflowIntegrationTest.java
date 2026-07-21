package com.motionecosystem.trainingplanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.audit.api.TransactionalOutbox;
import com.motionecosystem.consent.ConsentGrantService;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.safety.SafetyV2Service;
import com.motionecosystem.safety.SafetyV2Service.OverrideCommand;
import com.motionecosystem.safety.SafetyV2Service.RestrictionCommand;
import com.motionecosystem.safety.SafetyV2Service.TargetCommand;
import com.motionecosystem.safety.api.SafetyAssessmentPort.Result;
import com.motionecosystem.safety.domain.SafetyRules.SemanticType;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.support.PostgresTestConfiguration;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.AcknowledgeWarningCommand;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.ActivateWorkflowCommand;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.SafetyBlockException;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.ValidateWorkflowCommand;
import com.motionecosystem.trainingplanning.PlanCollaborationService.CollaborationScope;
import com.motionecosystem.trainingplanning.PlanCollaborationService.CollaboratorCommand;
import com.motionecosystem.trainingplanning.PlanCollaborationService.ReviewDecision;
import com.motionecosystem.trainingplanning.PlanCollaborationService.ReviewDecisionCommand;
import com.motionecosystem.trainingplanning.PlanCollaborationService.ReviewRequestCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.DoseType;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.GoalPerspective;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.IntensityType;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanMode;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PrescriptionSide;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddCycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddGoalCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddMicrocycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddPrescriptionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddSessionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateDraftCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateRevisionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.EditorView;
import com.motionecosystem.trainingplanning.api.PlanRevisionWorkflowPersistence.ActivationOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class PlanRevisionWorkflowIntegrationTest {

    @Autowired TrainingPlanningV2Service planning;
    @Autowired PlanCollaborationService collaboration;
    @Autowired PlanRevisionWorkflowService workflow;
    @Autowired SafetyV2Service safety;
    @Autowired ConsentGrantService consents;
    @Autowired TransactionalOutbox dispatcher;
    @Autowired JdbcTemplate jdbc;

    UUID participant;
    UUID otherParticipant;
    UUID trainer;
    UUID physio;
    UUID structure;
    UUID exerciseVersion;

    @BeforeEach
    void setUp() {
        participant = account("workflow-participant", "PARTICIPANT");
        otherParticipant = account("workflow-other", "PARTICIPANT");
        trainer = account("workflow-trainer", "SPECIALIST");
        physio = account("workflow-physio", "SPECIALIST");
        relationship(trainer);
        relationship(physio);
        scope(trainer, "TRAINER");
        scope(physio, "PHYSIOTHERAPIST");
        UUID template = consents.publishTemplate(
                "PLAN_WORKFLOW", 1, "urn:moves:consent:workflow:v1", "EXPLICIT_CONSENT").id();
        grant(trainer, template, ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING,
                ConsentDecisionPort.DataScope.PLAN);
        grant(physio, template, ConsentDecisionPort.Purpose.FUNCTIONAL_RECOVERY,
                ConsentDecisionPort.DataScope.PLAN);
        grant(physio, template, ConsentDecisionPort.Purpose.CLINICAL_REVIEW,
                ConsentDecisionPort.DataScope.CLINICAL_RATIONALE);
        structure = publishedStructure();
        exerciseVersion = publishedExerciseProfile();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.outbox_event,
                    audit.audit_event,
                    training_planning.training_plan,
                    load_analysis.planned_load_snapshot,
                    safety.assessment_override,
                    safety.assessment_factor,
                    safety.plan_safety_assessment,
                    safety.restriction_target,
                    safety.restriction,
                    consent.consent_template_version,
                    specialist.participant_specialist_relationship,
                    specialist.professional_scope,
                    exercise_catalog.exercise,
                    anatomy_reference.anatomical_structure,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void passActivationIsAtomicRetrySafeAndPublishesOnlyNeutralEvents() {
        EditorView editor = completePlan("workflow-participant", null, PlanMode.SELF_DIRECTED);
        UUID revisionId = editor.revision().revisionId();

        var validation = workflow.validate("workflow-participant", revisionId,
                new ValidateWorkflowCommand(version(editor), null));
        assertThat(validation.status()).isEqualTo("READY");
        assertThat(validation.assessment().recordedResult()).isEqualTo(Result.PASS);

        ActivationOutcome first = workflow.activate("workflow-participant", revisionId, "activate-pass",
                new ActivateWorkflowCommand(null));
        ActivationOutcome retry = workflow.activate("workflow-participant", revisionId, "activate-pass",
                new ActivateWorkflowCommand(null));

        assertThat(first.repeated()).isFalse();
        assertThat(retry.repeated()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM training_planning.plan_revision WHERE id=?", String.class, revisionId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM training_planning.plan_activation_request WHERE revision_id=?",
                Integer.class, revisionId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.outbox_event WHERE event_type='PlanRevisionActivated'",
                Integer.class)).isOne();
        List<String> payloads = jdbc.queryForList("SELECT payload FROM audit.outbox_event", String.class);
        assertThat(payloads).noneMatch(payload -> {
            String lower = payload.toLowerCase();
            return lower.contains("clinical") || lower.contains("rationale") || lower.contains("restriction");
        });

        java.util.ArrayList<UUID> delivered = new java.util.ArrayList<>();
        assertThat(dispatcher.dispatchPending(message -> delivered.add(message.id()))).isPositive();
        assertThat(dispatcher.dispatchPending(message -> delivered.add(message.id()))).isZero();
        assertThat(delivered).doesNotHaveDuplicates();
    }

    @Test
    void warningRequiresFactorAcknowledgementAndNewRestrictionRequiresRevalidation() {
        var restriction = safety.declareParticipantRestriction(
                "workflow-participant", participantRestriction(SemanticType.CAUTION));
        EditorView editor = completePlan("workflow-participant", null, PlanMode.SELF_DIRECTED);
        UUID revisionId = editor.revision().revisionId();
        var validation = workflow.validate("workflow-participant", revisionId,
                new ValidateWorkflowCommand(version(editor), null));

        assertThat(validation.status()).isEqualTo("NEEDS_REVIEW");
        assertConflict(() -> workflow.activate("workflow-participant", revisionId, "before-ack",
                new ActivateWorkflowCommand(null)));
        Set<UUID> warnings = validation.assessment().factors().stream()
                .filter(factor -> factor.result() == Result.WARNING)
                .map(item -> item.id())
                .collect(java.util.stream.Collectors.toSet());
        workflow.acknowledge("workflow-participant", revisionId,
                new AcknowledgeWarningCommand(warnings, "I reviewed the plan warning.", null));
        workflow.activate("workflow-participant", revisionId, "after-ack",
                new ActivateWorkflowCommand(null));

        safety.withdrawParticipantRestriction("workflow-participant", restriction.id());
        EditorView second = completePlan("workflow-participant", null, PlanMode.SELF_DIRECTED);
        UUID secondRevision = second.revision().revisionId();
        workflow.validate("workflow-participant", secondRevision,
                new ValidateWorkflowCommand(version(second), null));
        safety.declareParticipantRestriction(
                "workflow-participant", participantRestriction(SemanticType.CAUTION));
        assertConflict(() -> workflow.activate("workflow-participant", secondRevision, "stale",
                new ActivateWorkflowCommand(null)));
        long currentVersion = planning.editor("workflow-participant", secondRevision)
                .revision().revisionVersion();
        assertThat(workflow.validate("workflow-participant", secondRevision,
                new ValidateWorkflowCommand(currentVersion, null)).status()).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void clinicalHardBlockUsesSafeCodesAndOnlySafetyOverrideCanReleaseIt() {
        safety.createPhysiotherapistRestriction(
                physio,
                participant,
                new ActingContext(ProfessionalRole.PHYSIOTHERAPIST),
                clinicalRestriction());
        EditorView editor = completePlan("workflow-participant", null, PlanMode.SELF_DIRECTED);
        UUID revisionId = editor.revision().revisionId();
        var validation = workflow.validate("workflow-participant", revisionId,
                new ValidateWorkflowCommand(version(editor), null));
        assertThat(validation.status()).isEqualTo("BLOCKED");

        assertThatThrownBy(() -> workflow.activate("workflow-participant", revisionId, "blocked",
                new ActivateWorkflowCommand(null)))
                .isInstanceOfSatisfying(SafetyBlockException.class, error ->
                        assertThat(error.explanationCodes())
                                .containsExactly("SAFETY_RESTRICTION_INTERSECTION")
                                .allMatch(code -> !code.toLowerCase().contains("diagnosis")));
        var factor = validation.assessment().factors().stream()
                .filter(item -> item.result() == Result.HARD_BLOCK)
                .findFirst().orElseThrow();
        safety.overrideFactor(
                physio,
                participant,
                new ActingContext(ProfessionalRole.PHYSIOTHERAPIST),
                validation.assessment().id(),
                factor.id(),
                new OverrideCommand(
                        "CLINICAL_REVIEW_COMPLETE",
                        "THIS_FACTOR",
                        null,
                        Instant.now().plus(2, ChronoUnit.HOURS)));
        assertThat(workflow.activate("workflow-participant", revisionId, "overridden",
                new ActivateWorkflowCommand(null)).repeated()).isFalse();
    }

    @Test
    void trainerAndPhysiotherapistCollaborateWithoutClinicalLeakAndRevocationIsImmediate() {
        var clinical = safety.createPhysiotherapistRestriction(
                physio, participant, new ActingContext(ProfessionalRole.PHYSIOTHERAPIST),
                clinicalRestriction());
        var revisedClinical = safety.revisePhysiotherapistRestriction(
                physio, participant, new ActingContext(ProfessionalRole.PHYSIOTHERAPIST),
                clinical.id(), new RestrictionCommand(SemanticType.CONTRAINDICATION, null, null,
                        "Updated participant-visible planning constraint.",
                        "urn:clinical:updated-reference", target()));
        UUID template = jdbc.queryForObject("""
                SELECT id FROM consent.consent_template_version WHERE template_code='PLAN_WORKFLOW'
                """, UUID.class);
        UUID effectiveGrant = consents.grant("workflow-participant", new ConsentGrantService.GrantCommand(
                trainer, ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING, template,
                Set.of(ConsentDecisionPort.DataScope.EFFECTIVE_RESTRICTION), null, null)).id();

        var safeEnvelope = safety.effectiveRestrictions(trainer, participant,
                new ActingContext(ProfessionalRole.TRAINER));
        assertThat(safeEnvelope).singleElement().satisfies(item -> {
            assertThat(item.restrictionId()).isEqualTo(revisedClinical.id());
            assertThat(item.explanationCode()).isEqualTo("ACTIVE_CONTRAINDICATION");
            assertThat(item.toString()).doesNotContain("urn:clinical");
        });
        assertForbidden(() -> safety.clinicalRestrictions(trainer, participant,
                new ActingContext(ProfessionalRole.TRAINER)));
        assertThat(safety.clinicalRestrictions(physio, participant,
                new ActingContext(ProfessionalRole.PHYSIOTHERAPIST)))
                .extracting(item -> item.clinicalRationaleRef())
                .containsExactly("urn:clinical:test-reference", "urn:clinical:updated-reference");

        EditorView plan = completePlan("workflow-trainer", participant, PlanMode.COLLABORATIVE);
        var collaborator = collaboration.addCollaborator("workflow-trainer", plan.planId(),
                new ActingContext(ProfessionalRole.TRAINER),
                new CollaboratorCommand(physio, ProfessionalRole.PHYSIOTHERAPIST,
                        Set.of(CollaborationScope.VIEW_PLAN, CollaborationScope.REVIEW_SAFETY)));
        var validation = workflow.validate("workflow-trainer", plan.revision().revisionId(),
                new ValidateWorkflowCommand(version(plan), new ActingContext(ProfessionalRole.TRAINER)));
        assertThat(validation.status()).isEqualTo("BLOCKED");
        var review = collaboration.requestReview("workflow-trainer", plan.revision().revisionId(),
                new ReviewRequestCommand(physio, "review:blocked-envelope"));
        var decision = collaboration.decideReview("workflow-physio", review.id(),
                new ActingContext(ProfessionalRole.PHYSIOTHERAPIST),
                new ReviewDecisionCommand(ReviewDecision.PROPOSE_CHANGE, "proposal:reduce-left-load"));
        assertThat(decision.status()).isEqualTo("CHANGE_PROPOSED");
        collaboration.endCollaborator("workflow-trainer", plan.planId(), collaborator.id(),
                new ActingContext(ProfessionalRole.TRAINER));
        assertForbidden(() -> collaboration.requestReview("workflow-trainer", plan.revision().revisionId(),
                new ReviewRequestCommand(physio, "review:after-collaboration-ended")));

        consents.revoke("workflow-participant", effectiveGrant);
        assertForbidden(() -> safety.effectiveRestrictions(trainer, participant,
                new ActingContext(ProfessionalRole.TRAINER)));
        jdbc.update("""
                UPDATE specialist.participant_specialist_relationship
                SET status='ENDED', ended_at=now()
                WHERE specialist_account_id=? AND participant_account_id=?
                """, physio, participant);
        assertForbidden(() -> safety.clinicalRestrictions(physio, participant,
                new ActingContext(ProfessionalRole.PHYSIOTHERAPIST)));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit.audit_event
                WHERE event_type IN ('BLOCKED_PLAN_SENT_TO_PHYSIO_REVIEW', 'PLAN_REVIEW_PROPOSE_CHANGE')
                """, Long.class)).isEqualTo(2);
    }

    @Test
    void concurrentActivationSupersedesPreviousRevisionAndRejectsUnauthorizedActors() throws Exception {
        EditorView first = completePlan("workflow-participant", null, PlanMode.SELF_DIRECTED);
        UUID firstRevision = first.revision().revisionId();
        workflow.validate("workflow-participant", firstRevision,
                new ValidateWorkflowCommand(version(first), null));
        workflow.activate("workflow-participant", firstRevision, "first", new ActivateWorkflowCommand(null));

        EditorView next = planning.createRevision(
                "workflow-participant", first.planId(), new CreateRevisionCommand(firstRevision));
        UUID nextRevision = next.revision().revisionId();
        workflow.validate("workflow-participant", nextRevision,
                new ValidateWorkflowCommand(version(next), null));
        assertForbidden(() -> workflow.workflow("workflow-other", nextRevision, null));
        assertForbidden(() -> workflow.workflow(
                "workflow-trainer", nextRevision, new ActingContext(ProfessionalRole.TRAINER)));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> activateConcurrently(nextRevision, ready, start));
            var two = executor.submit(() -> activateConcurrently(nextRevision, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(false, true);
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM training_planning.plan_revision WHERE id=?", String.class, firstRevision))
                .isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject(
                "SELECT current_revision_id FROM training_planning.training_plan WHERE id=?",
                UUID.class, first.planId())).isEqualTo(nextRevision);
    }

    @Test
    void trainerAndPhysiotherapistNeedExplicitAuthorizedContextAndRollbackCreatesNoWorkflowEvent() {
        EditorView trainerPlan = completePlan("workflow-trainer", participant, PlanMode.SPECIALIST);
        assertForbidden(() -> workflow.validate("workflow-trainer", trainerPlan.revision().revisionId(),
                new ValidateWorkflowCommand(version(trainerPlan), null)));
        assertThat(workflow.validate("workflow-trainer", trainerPlan.revision().revisionId(),
                new ValidateWorkflowCommand(
                        version(trainerPlan), new ActingContext(ProfessionalRole.TRAINER))).status())
                .isEqualTo("READY");

        EditorView physioPlan = completePlan("workflow-physio", participant, PlanMode.SPECIALIST);
        assertThat(workflow.validate("workflow-physio", physioPlan.revision().revisionId(),
                new ValidateWorkflowCommand(
                        version(physioPlan), new ActingContext(ProfessionalRole.PHYSIOTHERAPIST))).status())
                .isEqualTo("READY");

        EditorView invalid = planning.createDraft("workflow-participant", new CreateDraftCommand(
                null, "Invalid workflow", "Rollback case", PlanMode.SELF_DIRECTED,
                "No structure", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        int eventsBefore = jdbc.queryForObject("SELECT count(*) FROM audit.outbox_event", Integer.class);
        assertConflict(() -> workflow.validate("workflow-participant", invalid.revision().revisionId(),
                new ValidateWorkflowCommand(version(invalid), null)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.outbox_event", Integer.class))
                .isEqualTo(eventsBefore);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM safety.plan_safety_assessment WHERE revision_id=?",
                Integer.class, invalid.revision().revisionId())).isZero();
    }

    @Test
    void withdrawnExerciseVersionPreventsNewAssignmentAfterValidation() {
        EditorView editor = completePlan("workflow-participant", null, PlanMode.SELF_DIRECTED);
        UUID revisionId = editor.revision().revisionId();
        workflow.validate("workflow-participant", revisionId,
                new ValidateWorkflowCommand(version(editor), null));

        jdbc.update("UPDATE exercise_catalog.exercise_version SET status='WITHDRAWN', withdrawn_at=now() WHERE id=?",
                exerciseVersion);

        assertConflict(() -> workflow.activate("workflow-participant", revisionId, "withdrawn",
                new ActivateWorkflowCommand(null)));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.outbox_event WHERE event_type='PlanRevisionActivated'",
                Integer.class)).isZero();
    }

    private boolean activateConcurrently(UUID revisionId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return workflow.activate("workflow-participant", revisionId, "parallel-key",
                new ActivateWorkflowCommand(null)).repeated();
    }

    private EditorView completePlan(String subject, UUID targetParticipant, PlanMode mode) {
        EditorView editor = planning.createDraft(subject, new CreateDraftCommand(
                targetParticipant,
                "Workflow plan " + UUID.randomUUID(),
                "Validate and activate safely",
                mode,
                "Foundation phase",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                targetParticipant == null ? null : new ActingContext(subject.contains("physio")
                        ? ProfessionalRole.PHYSIOTHERAPIST : ProfessionalRole.TRAINER)));
        UUID revisionId = editor.revision().revisionId();
        editor = planning.addGoal(subject, revisionId, new AddGoalCommand(
                version(editor),
                mode == PlanMode.SELF_DIRECTED ? GoalPerspective.GENERAL_FITNESS
                        : subject.contains("physio")
                                ? GoalPerspective.FUNCTIONAL_RECOVERY : GoalPerspective.PERFORMANCE,
                "CAPACITY",
                "Build controlled capacity",
                "Progress without exceeding constraints",
                1,
                null,
                LocalDate.of(2026, 8, 31),
                List.of()));
        editor = planning.addCycle(subject, revisionId, new AddCycleCommand(
                version(editor), 1, "Foundation", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31), "Build tolerance", "Complete sessions"));
        UUID cycle = editor.revision().cycles().getFirst().id();
        editor = planning.addMicrocycle(subject, revisionId, new AddMicrocycleCommand(
                version(editor), cycle, 1, "Week one", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7), "Controlled volume", "Complete session"));
        UUID microcycle = editor.revision().cycles().getFirst().microcycles().getFirst().id();
        editor = planning.addSession(subject, revisionId, new AddSessionCommand(
                version(editor), microcycle, "Strength session", LocalDate.of(2026, 8, 2),
                Instant.parse("2026-08-02T06:00:00Z"), Instant.parse("2026-08-02T20:00:00Z"), 45));
        UUID session = editor.revision().cycles().getFirst().microcycles().getFirst()
                .sessions().getFirst().id();
        return planning.addPrescription(subject, revisionId, new AddPrescriptionCommand(
                version(editor), session, exerciseVersion, 1, PrescriptionSide.LEFT,
                DoseType.DYNAMIC_RESISTANCE, 3, 8, null, null, null,
                null, null, IntensityType.RPE, BigDecimal.valueOf(7), null,
                null, "FULL", 60, null, null));
    }

    private RestrictionCommand participantRestriction(SemanticType type) {
        return new RestrictionCommand(
                type, null, null, "Participant-visible planning constraint.", null,
                target());
    }

    private RestrictionCommand clinicalRestriction() {
        return new RestrictionCommand(
                SemanticType.CONTRAINDICATION,
                null,
                null,
                "Participant-visible planning constraint.",
                "urn:clinical:test-reference",
                target());
    }

    private TargetCommand target() {
        return new TargetCommand(
                structure, null, "DYN_EXU", null, "LEFT", null, null,
                null, null, null, null);
    }

    private UUID publishedStructure() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO anatomy_reference.anatomical_structure
                    (id, code, type, display_name, side_policy, status, taxonomy_version,
                     created_by_subject, created_at, published_at, version)
                VALUES (?, ?, 'MUSCLE_GROUP', 'Workflow structure', 'LEFT_RIGHT', 'PUBLISHED', 1,
                        'workflow-test', now(), now(), 0)
                """, id, "WORKFLOW_" + id.toString().substring(0, 8).toUpperCase());
        return id;
    }

    private UUID publishedExerciseProfile() {
        UUID exercise = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID contribution = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise
                    (id, canonical_name, created_at, created_by_subject)
                VALUES (?, 'Workflow split squat', now(), 'workflow-test')
                """, exercise);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version
                    (id, exercise_id, version_number, status, instruction, movement_pattern,
                     stimulus_type, fatigue_profile, technical_level, environment,
                     profile_schema_version, reviewed_by_subject, reviewed_at,
                     created_at, published_at, version)
                VALUES (?, ?, 1, 'APPROVED', 'Controlled split squat.', 'SQUAT',
                        'STRENGTH', 'MODERATE', 'FOUNDATIONAL', 'ANY', 2,
                        'workflow-reviewer', now(), now(), NULL, 0)
                """, version, exercise);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version_movement_pattern
                    (exercise_version_id, movement_pattern) VALUES (?, 'SQUAT')
                """, version);
        jdbc.update("""
                INSERT INTO exercise_catalog.evidence_source
                    (id, exercise_version_id, citation, source_uri, evidence_grade,
                     created_at, created_by_subject)
                VALUES (?, ?, 'Workflow test evidence', 'https://example.test/workflow',
                        'EDITORIAL_REVIEW', now(), 'workflow-test')
                """, evidence, version);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_contribution
                    (id, exercise_version_id, anatomical_structure_id, contribution_role,
                     load_channel, contribution_band, coefficient_low, coefficient_high,
                     confidence_class, evidence_grade, calculation_role, variant_condition,
                     side_rule, created_at, created_by_subject)
                VALUES (?, ?, ?, 'PRIMARY', 'DYN_EXU', 'HIGH', 0.500000, 0.700000,
                        'MODERATE', 'EDITORIAL_REVIEW', 'ALLOCATION', 'STANDARD',
                        'AS_PRESCRIBED', now(), 'workflow-test')
                """, contribution, version, structure);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_contribution_evidence
                    (id, contribution_id, evidence_source_id) VALUES (?, ?, ?)
                """, UUID.randomUUID(), contribution, evidence);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_review
                    (id, exercise_version_id, review_area, decision, reviewer_subject, reviewed_at)
                SELECT gen_random_uuid(), ?, area, 'APPROVED', 'workflow-fixture-reviewer', now()
                FROM unnest(ARRAY['CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE']) area
                """, version);
        jdbc.update("""
                UPDATE exercise_catalog.exercise_version
                SET status = 'PUBLISHED', published_at = now()
                WHERE id = ?
                """, version);
        return version;
    }

    private UUID account(String subject, String profile) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_access.principal_account
                    (id, external_subject, status, profile_type, created_at, version)
                VALUES (?, ?, 'ACTIVE', ?, now(), 0)
                """, id, subject, profile);
        return id;
    }

    private void relationship(UUID specialist) {
        jdbc.update("""
                INSERT INTO specialist.participant_specialist_relationship
                    (id, specialist_account_id, participant_account_id, status, activated_at)
                VALUES (?, ?, ?, 'ACTIVE', now())
                """, UUID.randomUUID(), specialist, participant);
    }

    private void scope(UUID specialist, String type) {
        jdbc.update("""
                INSERT INTO specialist.professional_scope
                    (specialist_account_id, scope_type, verification_status, verified_at, created_at)
                VALUES (?, ?, 'VERIFIED', now(), now())
                """, specialist, type);
    }

    private void grant(
            UUID recipient,
            UUID template,
            ConsentDecisionPort.Purpose purpose,
            ConsentDecisionPort.DataScope scope) {
        consents.grant("workflow-participant", new ConsentGrantService.GrantCommand(
                recipient, purpose, template, Set.of(scope), null, null));
    }

    private static long version(EditorView editor) {
        return editor.revision().revisionVersion();
    }

    private static void assertConflict(Runnable action) {
        assertStatus(HttpStatus.CONFLICT, action);
    }

    private static void assertForbidden(Runnable action) {
        assertStatus(HttpStatus.FORBIDDEN, action);
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(expected));
    }
}
