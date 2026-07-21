package com.motionecosystem.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class OnboardingIntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FilterChainProxy securityFilterChain;
    @Autowired
    JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    @AfterEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.audit_event,
                    availability.recurring_slot,
                    consent.legal_acknowledgement,
                    specialist.specialist_profile,
                    participant.participant_profile,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void participantCompletesOnboardingWithIdempotentVersionedAcknowledgements() throws Exception {
        mvc.perform(get("/api/v1/onboarding"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/onboarding").with(subject("participant-one")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PROFILE_TYPE_REQUIRED"))
                .andExpect(jsonPath("$.missingSteps.length()").value(3));

        put("participant-one", "/profile-type", "{\"profileType\":\"PARTICIPANT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LEGAL_REQUIRED"));

        put("participant-one", "/legal-acknowledgements",
                "{\"termsAccepted\":true,\"privacyNoticeAcknowledged\":false}")
                .andExpect(status().isBadRequest());

        String legal = "{\"termsAccepted\":true,\"privacyNoticeAcknowledged\":true}";
        put("participant-one", "/legal-acknowledgements", legal).andExpect(status().isOk());
        put("participant-one", "/legal-acknowledgements", legal).andExpect(status().isOk());
        assertThat(countFor("consent.legal_acknowledgement", "participant-one")).isEqualTo(2);

        put("participant-one", "/participant-profile", "{\"displayName\":\" Ala \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.displayName").value("Ala"))
                .andExpect(jsonPath("$.stage").value("AVAILABILITY_REQUIRED"));

        put("participant-one", "/availability", availability("No/Zone", "09:00", "10:00", null))
                .andExpect(status().isBadRequest());
        put("participant-one", "/availability", availability("Europe/Warsaw", "10:00", "09:00", null))
                .andExpect(status().isBadRequest());
        put("participant-one", "/availability", availability("Europe/Warsaw", "09:00", "11:00", "10:00"))
                .andExpect(status().isBadRequest());

        put("participant-one", "/availability", availability("Europe/Warsaw", "09:00", "11:00", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("READY"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE actor_subject = ?", Long.class, "participant-one"))
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void isolatesSubjectsHonoursNewDocumentVersionAndRejectsInactiveAccount() throws Exception {
        mvc.perform(get("/api/v1/onboarding").with(subject("primary"))).andExpect(status().isOk());
        UUID primaryId = accountId("primary");
        jdbc.update("""
                INSERT INTO consent.legal_acknowledgement
                    (id, account_id, acknowledgement_type, document_version, accepted_at)
                VALUES (?, ?, 'TERMS_OF_SERVICE', 'terms-v0', now()),
                       (?, ?, 'PRIVACY_NOTICE', 'privacy-v1', now())
                """, UUID.randomUUID(), primaryId, UUID.randomUUID(), primaryId);

        put("primary", "/profile-type", "{\"profileType\":\"PARTICIPANT\"}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/onboarding").with(subject("primary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LEGAL_REQUIRED"));
        put("primary", "/legal-acknowledgements",
                "{\"termsAccepted\":true,\"privacyNoticeAcknowledged\":true}")
                .andExpect(status().isOk());
        assertThat(countFor("consent.legal_acknowledgement", "primary")).isEqualTo(3);

        mvc.perform(get("/api/v1/onboarding").with(subject("other")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileType").doesNotExist())
                .andExpect(jsonPath("$.currentLegalAcknowledgements.length()").value(0));

        jdbc.update("UPDATE identity_access.principal_account SET status = 'SUSPENDED' WHERE id = ?", primaryId);
        mvc.perform(get("/api/v1/onboarding").with(subject("primary")))
                .andExpect(status().isForbidden());
    }

    @Test
    void specialistKindIsDomainDataAndLegacyEndpointCanAddASecondProfile() throws Exception {
        put("specialist", "/profile-type", "{\"profileType\":\"SPECIALIST\"}")
                .andExpect(status().isOk());
        put("specialist", "/profile-type", "{\"profileType\":\"PARTICIPANT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileType").value("SPECIALIST"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity_access.account_domain_profile profile
                JOIN identity_access.principal_account account ON account.id = profile.account_id
                WHERE account.external_subject = 'specialist' AND profile.status = 'ACTIVE'
                """, Integer.class)).isEqualTo(2);
        put("specialist", "/legal-acknowledgements",
                "{\"termsAccepted\":true,\"privacyNoticeAcknowledged\":true}")
                .andExpect(status().isOk());
        put("specialist", "/specialist-profile", "{\"displayName\":\"Jan\"}")
                .andExpect(status().isBadRequest());
        put("specialist", "/specialist-profile",
                "{\"displayName\":\"Jan\",\"specialistKind\":\"PHYSIOTHERAPIST\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.specialistKind").value("PHYSIOTHERAPIST"));
        put("specialist", "/availability", availability("Europe/Warsaw", "12:00", "14:00", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("READY"));
    }

    private org.springframework.test.web.servlet.ResultActions put(String subject, String path, String body)
            throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/onboarding" + path)
                .with(subject(subject))
                .contentType("application/json")
                .content(body));
    }

    private static JwtRequestPostProcessor subject(String subject) {
        return jwt().jwt(builder -> builder.subject(subject).audience(List.of("motion-api")));
    }

    private UUID accountId(String subject) {
        return jdbc.queryForObject(
                "SELECT id FROM identity_access.principal_account WHERE external_subject = ?", UUID.class, subject);
    }

    private long countFor(String table, String subject) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " item "
                + "JOIN identity_access.principal_account account ON account.id = item.account_id "
                + "WHERE account.external_subject = ?", Long.class, subject);
    }

    private static String availability(String zone, String start, String end, String overlapStart) {
        String second = overlapStart == null ? "" : ",{" +
                "\"dayOfWeek\":\"MONDAY\",\"startTime\":\"" + overlapStart
                + "\",\"endTime\":\"12:00\",\"timeZone\":\"" + zone + "\"}";
        return "{\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\",\"timeZone\":\"" + zone + "\"}" + second + "]}";
    }
}
