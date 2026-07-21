package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.consent.ConsentGrantService;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.support.PostgresTestConfiguration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class AuthorizationAndConsentIntegrationTest {

    @Autowired
    private CurrentAccountService accounts;

    @Autowired
    private ConsentGrantService consents;

    @Autowired
    private ConsentDecisionPort decisions;

    @Autowired
    private SpecialistAuthorizationPort authorization;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mvc;

    private UUID participant;
    private UUID trainer;
    private UUID physio;
    private UUID template;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        participant = account("cap-participant", "PARTICIPANT");
        trainer = account("cap-trainer", "SPECIALIST");
        physio = account("cap-physio", "SPECIALIST");
        scope(trainer, "TRAINER");
        scope(physio, "PHYSIOTHERAPIST");
        relationship(trainer);
        relationship(physio);
        template = consents.publishTemplate(
                "SPECIAL_DATA",
                1,
                "urn:consent:special:v1",
                "EXPLICIT_CONSENT").id();
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE audit.audit_event,
                    consent.consent_template_version,
                    specialist.participant_specialist_relationship,
                    specialist.professional_scope,
                    identity_access.principal_account CASCADE
                """);
    }

    @Test
    void supportsMultipleProfilesAndExplicitProfessionalContext() {
        accounts.requireActive("multi-profile");
        accounts.selectProfileType("multi-profile", ProfileType.PARTICIPANT);
        var multi = accounts.selectProfileType("multi-profile", ProfileType.SPECIALIST);

        assertThat(multi.profiles())
                .containsExactlyInAnyOrder(ProfileType.PARTICIPANT, ProfileType.SPECIALIST);

        grant(
                trainer,
                ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING,
                Set.of(ConsentDecisionPort.DataScope.PLAN),
                null);

        var result = authorization.requireCapabilities(
                trainer,
                participant,
                new ActingContext(ProfessionalRole.TRAINER),
                Set.of(Capability.PLAN_PERFORMANCE),
                SpecialistAuthorizationPort.Purpose.PERFORMANCE_PLANNING);

        assertThat(result.actingRole()).isEqualTo(ProfessionalRole.TRAINER);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM audit.audit_event
                        WHERE event_type = 'CAPABILITY_TRAINER_PLAN_PERFORMANCE_PERFORMANCE_PLANNING'
                          AND aggregate_type = 'Participant'
                          AND aggregate_id = ?
                        """,
                        Integer.class,
                        participant))
                .isEqualTo(1);

        denied(() -> authorization.requireCapabilities(
                trainer,
                participant,
                new ActingContext(ProfessionalRole.TRAINER),
                Set.of(Capability.OVERRIDE_CLINICAL_BLOCK),
                SpecialistAuthorizationPort.Purpose.CLINICAL_REVIEW));
        denied(() -> authorization.requireCapabilities(
                trainer,
                participant,
                new ActingContext(ProfessionalRole.PHYSIOTHERAPIST),
                Set.of(Capability.PLAN_FUNCTIONAL_RECOVERY),
                SpecialistAuthorizationPort.Purpose.FUNCTIONAL_RECOVERY));
    }

    @Test
    void consentIsRecipientScopePurposeTimeBoundAndImmediatelyRevocable() {
        var grant = grant(
                physio,
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW,
                Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                null);

        assertThat(decisions.requireAccess(
                        physio,
                        participant,
                        Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                        ConsentDecisionPort.Purpose.CLINICAL_REVIEW)
                .grantId())
                .isEqualTo(grant.id());

        denied(() -> decisions.requireAccess(
                trainer,
                participant,
                Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW));
        denied(() -> decisions.requireAccess(
                physio,
                participant,
                Set.of(ConsentDecisionPort.DataScope.PLAN),
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW));

        consents.revoke("cap-participant", grant.id());

        denied(() -> decisions.requireAccess(
                physio,
                participant,
                Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW));

        grant(
                physio,
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW,
                Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                Instant.parse("2020-01-02T00:00:00Z"));

        denied(() -> decisions.requireAccess(
                physio,
                participant,
                Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW));
    }

    @Test
    void legalAcknowledgementIsNotConsentAndEndedRelationshipRevokesCapability() {
        jdbc.update("""
                        INSERT INTO consent.legal_acknowledgement
                            (id, account_id, acknowledgement_type, document_version, accepted_at)
                        VALUES (?, ?, 'PRIVACY_NOTICE', '2026-01', now())
                        """,
                UUID.randomUUID(),
                participant);

        denied(() -> decisions.requireAccess(
                physio,
                participant,
                Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW));

        grant(
                physio,
                ConsentDecisionPort.Purpose.CLINICAL_REVIEW,
                Set.of(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE),
                null);
        jdbc.update("""
                UPDATE specialist.participant_specialist_relationship
                SET status = 'ENDED', ended_at = now()
                WHERE specialist_account_id = ?
                """, physio);

        denied(() -> authorization.requireCapabilities(
                physio,
                participant,
                new ActingContext(ProfessionalRole.PHYSIOTHERAPIST),
                Set.of(Capability.VIEW_CLINICAL_RATIONALE),
                SpecialistAuthorizationPort.Purpose.CLINICAL_REVIEW));
    }

    @Test
    void apiCannotTurnATechnicalParticipantRoleIntoParticipantResourceOwnership() throws Exception {
        mvc.perform(post("/api/v1/consent/grants")
                        .with(jwt().jwt(token -> token.subject("cap-trainer"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientId": "%s",
                                  "purpose": "PERFORMANCE_PLANNING",
                                  "templateVersionId": "%s",
                                  "dataScopes": ["PLAN"]
                                }
                                """.formatted(physio, template)))
                .andExpect(status().isForbidden());

        String openApi = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(openApi).contains(
                "/api/v1/consent/templates",
                "/api/v1/consent/grants",
                "/api/v1/consent/grants/{grantId}/revoke");
    }

    private ConsentGrantService.GrantView grant(
            UUID recipient,
            ConsentDecisionPort.Purpose purpose,
            Set<ConsentDecisionPort.DataScope> scopes,
            Instant expiredTo) {
        Instant from = expiredTo == null ? null : Instant.parse("2020-01-01T00:00:00Z");
        return consents.grant(
                "cap-participant",
                new ConsentGrantService.GrantCommand(
                        recipient,
                        purpose,
                        template,
                        scopes,
                        from,
                        expiredTo));
    }

    private UUID account(String subject, String profile) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO identity_access.principal_account
                            (id, external_subject, status, profile_type, created_at, version)
                        VALUES (?, ?, 'ACTIVE', ?, now(), 0)
                        """,
                id,
                subject,
                profile);
        return id;
    }

    private void scope(UUID actor, String type) {
        jdbc.update("""
                        INSERT INTO specialist.professional_scope
                            (specialist_account_id, scope_type, verification_status, verified_at, created_at)
                        VALUES (?, ?, 'VERIFIED', now(), now())
                        """,
                actor,
                type);
    }

    private void relationship(UUID actor) {
        jdbc.update("""
                        INSERT INTO specialist.participant_specialist_relationship
                            (id, specialist_account_id, participant_account_id, status, activated_at)
                        VALUES (?, ?, ?, 'ACTIVE', now())
                        """,
                UUID.randomUUID(),
                actor,
                participant);
    }

    private static void denied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
