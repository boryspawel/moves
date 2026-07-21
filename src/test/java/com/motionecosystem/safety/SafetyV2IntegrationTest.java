package com.motionecosystem.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.consent.ConsentGrantService;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.Observation;
import com.motionecosystem.safety.SafetyV2Service.OverrideCommand;
import com.motionecosystem.safety.SafetyV2Service.RestrictionCommand;
import com.motionecosystem.safety.SafetyV2Service.TargetCommand;
import com.motionecosystem.safety.api.SafetyAssessmentPort.Result;
import com.motionecosystem.safety.domain.SafetyRules.SemanticType;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.support.PostgresTestConfiguration;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.CycleSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.MicrocycleSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.SessionSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
class SafetyV2IntegrationTest {

    @Autowired
    private SafetyV2Service safety;
    @Autowired
    private ConsentGrantService consents;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID participant;
    private UUID trainer;
    private UUID physio;
    private UUID structure;
    private UUID consentTemplate;

    @BeforeEach
    void setUp() {
        participant = account("safety-participant", "PARTICIPANT");
        trainer = account("safety-trainer", "SPECIALIST");
        physio = account("safety-physio", "SPECIALIST");
        scope(trainer, "TRAINER");
        scope(physio, "PHYSIOTHERAPIST");
        relationship(physio);
        relationship(trainer);
        structure = structure();
        consentTemplate = consents.publishTemplate(
                "SAFETY_CLINICAL", 1, "urn:safety:consent:v1", "EXPLICIT_CONSENT").id();
        consents.grant(
                "safety-participant",
                new ConsentGrantService.GrantCommand(
                        physio,
                        ConsentDecisionPort.Purpose.CLINICAL_REVIEW,
                        consentTemplate,
                        Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                        null,
                        null));
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE audit.audit_event,
                    safety.assessment_override,
                    safety.assessment_factor,
                    safety.plan_safety_assessment,
                    safety.restriction_target,
                    safety.restriction,
                    safety.participant_restriction,
                    consent.consent_template_version,
                    specialist.participant_specialist_relationship,
                    specialist.professional_scope,
                    anatomy_reference.anatomical_structure,
                    identity_access.principal_account CASCADE
                """);
    }

    @Test
    void participantDeclarationsAreRevisionedAndCannotChangeClinicalRestrictions() {
        var first = safety.declareParticipantRestriction(
                "safety-participant", participantCommand(SemanticType.CAUTION, null));
        var second = safety.reviseParticipantRestriction(
                "safety-participant", first.id(), participantCommand(SemanticType.MONITOR, null));

        assertThat(second.rootId()).isEqualTo(first.rootId());
        assertThat(second.revisionNumber()).isEqualTo(2);
        assertThat(safety.participantHistory("safety-participant"))
                .extracting(SafetyV2Service.RestrictionView::status)
                .containsExactly("SUPERSEDED", "ACTIVE");

        var clinical = safety.createPhysiotherapistRestriction(
                physio,
                participant,
                context(ProfessionalRole.PHYSIOTHERAPIST),
                clinicalCommand(null));
        assertThat(clinical.toString()).doesNotContain("secret-clinical-note");
        denied(() -> safety.withdrawParticipantRestriction("safety-participant", clinical.id()));
    }

    @Test
    void trainerAndPhysioWithoutRelationshipOrConsentCannotCreateClinicalRestriction() {
        denied(() -> safety.createPhysiotherapistRestriction(
                trainer,
                participant,
                context(ProfessionalRole.TRAINER),
                clinicalCommand(null)));

        UUID unrelatedPhysio = account("safety-unrelated-physio", "SPECIALIST");
        scope(unrelatedPhysio, "PHYSIOTHERAPIST");
        denied(() -> safety.createPhysiotherapistRestriction(
                unrelatedPhysio,
                participant,
                context(ProfessionalRole.PHYSIOTHERAPIST),
                clinicalCommand(null)));

        UUID noConsentPhysio = account("safety-no-consent-physio", "SPECIALIST");
        scope(noConsentPhysio, "PHYSIOTHERAPIST");
        relationship(noConsentPhysio);
        denied(() -> safety.createPhysiotherapistRestriction(
                noConsentPhysio,
                participant,
                context(ProfessionalRole.PHYSIOTHERAPIST),
                clinicalCommand(null)));
    }

    @Test
    void assessmentIsImmutableAndOverrideIsFactorScopedAndTimeBound() {
        safety.createPhysiotherapistRestriction(
                physio,
                participant,
                context(ProfessionalRole.PHYSIOTHERAPIST),
                clinicalCommand(null));
        PlanRevisionSnapshot revision = revision();
        LoadProfile load = load(revision);

        var assessment = safety.assess(participant, revision, load);

        assertThat(assessment.recordedResult()).isEqualTo(Result.HARD_BLOCK);
        var factor = assessment.factors().stream()
                .filter(item -> item.result() == Result.HARD_BLOCK)
                .findFirst().orElseThrow();
        assertThat(factor.explanationCode()).isEqualTo("SAFETY_RESTRICTION_INTERSECTION");
        assertThat(factor.targetRef()).contains(structure.toString());

        jdbc.update("UPDATE safety.restriction SET status = 'WITHDRAWN'");
        assertThat(safety.findAssessment(assessment.id(), Instant.now()).orElseThrow().recordedResult())
                .isEqualTo(Result.HARD_BLOCK);

        Instant validTo = Instant.now().plus(2, ChronoUnit.HOURS);
        safety.overrideFactor(
                physio,
                participant,
                context(ProfessionalRole.PHYSIOTHERAPIST),
                assessment.id(),
                factor.id(),
                new OverrideCommand("REVIEWED_CASE", "THIS_FACTOR", null, validTo));
        assertThat(safety.findAssessment(assessment.id(), Instant.now()).orElseThrow().effectiveResult())
                .isEqualTo(Result.PASS);
        assertThat(safety.findAssessment(assessment.id(), validTo.plusSeconds(1)).orElseThrow().effectiveResult())
                .isEqualTo(Result.HARD_BLOCK);

        denied(() -> safety.overrideFactor(
                trainer,
                participant,
                context(ProfessionalRole.TRAINER),
                assessment.id(),
                factor.id(),
                new OverrideCommand("NO", "ALL", null, validTo)));
    }

    @Test
    void expiredRestrictionDoesNotAffectNewAssessmentAndLegacyTagsStayUnmapped() {
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant lastWeek = Instant.now().minus(7, ChronoUnit.DAYS);
        safety.declareParticipantRestriction(
                "safety-participant",
                participantCommand(SemanticType.CONTRAINDICATION, new Validity(lastWeek, yesterday)));
        jdbc.update("""
                INSERT INTO safety.participant_restriction
                    (id, account_id, contraindication_tag, recorded_at)
                VALUES (?, ?, 'LEGACY_KNEE', now())
                """, UUID.randomUUID(), participant);

        PlanRevisionSnapshot revision = revision();
        var result = safety.assess(participant, revision, load(revision));

        assertThat(result.recordedResult()).isEqualTo(Result.PASS);
        assertThat(safety.legacyReport().unmappedParticipantTags()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM safety.restriction WHERE participant_explanation LIKE '%LEGACY_KNEE%'",
                Integer.class)).isZero();
    }

    private RestrictionCommand participantCommand(SemanticType semantic, Validity validity) {
        return new RestrictionCommand(
                semantic,
                validity == null ? null : validity.from(),
                validity == null ? null : validity.to(),
                "Participant-visible declaration for specialist review; not a diagnosis.",
                null,
                target(null, null));
    }

    private RestrictionCommand clinicalCommand(Validity validity) {
        return new RestrictionCommand(
                SemanticType.CONTRAINDICATION,
                validity == null ? null : validity.from(),
                validity == null ? null : validity.to(),
                "Participant-visible restriction explanation; not a diagnosis.",
                "urn:clinical:secret-clinical-note",
                target(null, null));
    }

    private TargetCommand target(BigDecimal limit, Integer recoveryHours) {
        return new TargetCommand(
                structure,
                null,
                "VOLUME",
                "EXTERNAL_LOAD",
                "LEFT",
                null,
                null,
                null,
                limit,
                limit == null ? null : "KG_REPETITION",
                recoveryHours);
    }

    private PlanRevisionSnapshot revision() {
        UUID revisionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 7, 21);
        SessionSnapshot session = new SessionSnapshot(
                sessionId, "Safety session", date, null, null, 60, "PLANNED", List.of());
        MicrocycleSnapshot microcycle = new MicrocycleSnapshot(
                UUID.randomUUID(), 1, "Micro", date, date, null, null, List.of(session));
        CycleSnapshot cycle = new CycleSnapshot(
                UUID.randomUUID(), 1, "Cycle", date, date, null, null, List.of(microcycle));
        return new PlanRevisionSnapshot(
                revisionId,
                UUID.randomUUID(),
                participant,
                1,
                null,
                0,
                "DRAFT",
                trainer,
                "PLAN_PERFORMANCE",
                Instant.now(),
                null,
                "PENDING",
                null,
                date,
                date,
                List.of(),
                List.of(cycle),
                List.of());
    }

    private LoadProfile load(PlanRevisionSnapshot revision) {
        UUID sessionId = revision.cycles().getFirst().microcycles().getFirst().sessions().getFirst().id();
        Observation observation = new Observation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                sessionId,
                revision.cycles().getFirst().microcycles().getFirst().id(),
                revision.cycles().getFirst().id(),
                structure,
                "LEFT",
                "VOLUME",
                "EXTERNAL_LOAD",
                "KG_REPETITION",
                BigDecimal.valueOf(12),
                BigDecimal.valueOf(12),
                "HIGH",
                "CURATED",
                "PRESCRIPTION",
                "DIRECT");
        return new LoadProfile(
                UUID.randomUUID(),
                revision.revisionId(),
                "a".repeat(64),
                "load-v1",
                "config-v1",
                "catalog-v2",
                Instant.now(),
                List.of(observation),
                List.of());
    }

    private UUID account(String subject, String profile) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO identity_access.principal_account
                            (id, external_subject, status, profile_type, created_at, version)
                        VALUES (?, ?, 'ACTIVE', ?, now(), 0)
                        """,
                id, subject, profile);
        return id;
    }

    private void scope(UUID actor, String type) {
        jdbc.update("""
                        INSERT INTO specialist.professional_scope
                            (specialist_account_id, scope_type, verification_status, verified_at, created_at)
                        VALUES (?, ?, 'VERIFIED', now(), now())
                        """,
                actor, type);
    }

    private void relationship(UUID actor) {
        jdbc.update("""
                        INSERT INTO specialist.participant_specialist_relationship
                            (id, specialist_account_id, participant_account_id, status, activated_at)
                        VALUES (?, ?, ?, 'ACTIVE', now())
                        """,
                UUID.randomUUID(), actor, participant);
    }

    private UUID structure() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO anatomy_reference.anatomical_structure
                            (id, code, type, display_name, side_policy, status, taxonomy_version,
                             created_by_subject, created_at, published_at, version)
                        VALUES (?, ?, 'JOINT', 'Safety joint', 'LEFT_RIGHT', 'PUBLISHED', 1,
                                'safety-test', now(), now(), 0)
                        """,
                id, "SAFETY_JOINT_" + id.toString().substring(0, 8).toUpperCase());
        return id;
    }

    private static ActingContext context(ProfessionalRole role) {
        return new ActingContext(role);
    }

    private static void denied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private record Validity(Instant from, Instant to) {
    }
}
