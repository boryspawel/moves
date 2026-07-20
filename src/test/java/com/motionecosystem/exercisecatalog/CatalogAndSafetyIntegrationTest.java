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
class CatalogAndSafetyIntegrationTest {

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
    void clean() {
        jdbc.execute("""
                TRUNCATE TABLE
                    audit.audit_event,
                    safety.readiness_check_in,
                    safety.participant_restriction,
                    exercise_catalog.exercise_version_equipment,
                    exercise_catalog.exercise_version_contraindication,
                    exercise_catalog.exercise_version,
                    exercise_catalog.exercise,
                    identity_access.principal_account
                CASCADE
                """);
    }

    @Test
    void contentAdminPublishesImmutableVersionAndParticipantUsesAllowedFilters() throws Exception {
        mvc.perform(post("/api/v1/admin/exercises")
                        .with(participant("participant"))
                        .contentType("application/json")
                        .content(createExercise()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/exercises")
                        .with(contentAdmin())
                        .contentType("application/json")
                        .content(createExercise()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        UUID versionId = jdbc.queryForObject(
                "SELECT id FROM exercise_catalog.exercise_version", UUID.class);

        mvc.perform(get("/api/v1/exercises").with(participant("participant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/publish", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/v1/exercises")
                        .with(participant("participant"))
                        .param("movementPattern", "SQUAT")
                        .param("technicalLevel", "FOUNDATIONAL")
                        .param("equipment", "band"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contraindicationTags[0]").value("ACUTE_KNEE_PAIN"));

        mvc.perform(get("/api/v1/exercises")
                        .with(participant("participant"))
                        .param("excludedContraindicationTag", "acute_knee_pain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(put("/api/v1/admin/exercises/versions/{id}", versionId)
                        .with(contentAdmin())
                        .contentType("application/json")
                        .content(versionCommand()))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/withdraw", versionId)
                        .with(contentAdmin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/exercises").with(participant("participant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void safetyInputsAreNonDiagnosticAndIsolatedPerAuthenticatedSubject() throws Exception {
        mvc.perform(get("/api/v1/safety/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/v1/safety/me/restrictions")
                        .with(participant("first"))
                        .contentType("application/json")
                        .content("{\"contraindicationTags\":[\"acute_knee_pain\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contraindicationTags[0]").value("ACUTE_KNEE_PAIN"));

        mvc.perform(post("/api/v1/safety/me/check-ins")
                        .with(participant("first"))
                        .contentType("application/json")
                        .content("{\"painLevel\":7,\"readinessLevel\":2,\"painArea\":\"knee\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestCheckIn.painLevel").value(7))
                .andExpect(jsonPath("$.notice").value(
                        "User-reported input for specialist review; this is not a diagnosis."));

        mvc.perform(post("/api/v1/safety/me/check-ins")
                        .with(participant("first"))
                        .contentType("application/json")
                        .content("{\"painLevel\":11,\"readinessLevel\":2}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/safety/me").with(participant("second")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contraindicationTags.length()").value(0))
                .andExpect(jsonPath("$.latestCheckIn").doesNotExist());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM safety.readiness_check_in", Long.class)).isEqualTo(1);
    }

    private static JwtRequestPostProcessor participant(String subject) {
        return jwt().jwt(builder -> builder.subject(subject).audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
    }

    private static JwtRequestPostProcessor contentAdmin() {
        return jwt().jwt(builder -> builder.subject("catalog-admin").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN"));
    }

    private static String createExercise() {
        return "{\"canonicalName\":\"Supported squat\",\"version\":" + versionCommand() + "}";
    }

    private static String versionCommand() {
        return """
                {
                  "instruction":"Stand with support and perform a controlled squat.",
                  "mediaReference":"s3://catalog/supported-squat-v1.mp4",
                  "movementPattern":"SQUAT",
                  "stimulusType":"STRENGTH",
                  "fatigueProfile":"MODERATE",
                  "technicalLevel":"FOUNDATIONAL",
                  "environment":"ANY",
                  "requiredEquipment":["band"],
                  "contraindicationTags":["acute_knee_pain"]
                }
                """;
    }
}
