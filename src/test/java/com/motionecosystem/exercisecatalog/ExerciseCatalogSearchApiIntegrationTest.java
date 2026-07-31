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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class ExerciseCatalogSearchApiIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy securityFilterChain;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    @Test
    void searchesPersistedPublishedVersionsByNormalizedPolishAliasWithFacetsCursorAndPreview() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PublishedExercise first = createPublishedExercise("Search alpha " + suffix);
        PublishedExercise second = createPublishedExercise("Search beta " + suffix);
        String alias = "Przysiad Łączony " + suffix;
        persistAlias(first.exerciseId(), alias);
        persistAlias(second.exerciseId(), alias + " drugi");

        String request = """
                {"query":"przysiad laczony %s","movementPatterns":["SQUAT"],"sort":"NAME","limit":1}
                """.formatted(suffix);
        MvcResult firstPage = mvc.perform(post("/api/v2/exercises/search").with(participant())
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].selectable").value(true))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.facets[0].group").value("movementPatterns"))
                .andExpect(jsonPath("$.facets[0].value").value("SQUAT"))
                .andExpect(jsonPath("$.facets[0].count").value(2))
                .andExpect(jsonPath("$.facets[0].active").value(true))
                .andReturn();
        JsonNode firstBody = json.readTree(firstPage.getResponse().getContentAsString());
        UUID firstVersion = UUID.fromString(firstBody.path("results").get(0).path("exerciseVersionId").asText());
        String cursor = firstBody.path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        String pagedRequest = request.strip();
        MvcResult secondPage = mvc.perform(post("/api/v2/exercises/search").with(participant())
                        .contentType("application/json")
                        .content(pagedRequest.substring(0, pagedRequest.length() - 1) + ",\"cursor\":\"" + cursor + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn();
        UUID secondVersion = UUID.fromString(json.readTree(secondPage.getResponse().getContentAsString())
                .path("results").get(0).path("exerciseVersionId").asText());
        assertThat(secondVersion).isNotEqualTo(firstVersion);

        mvc.perform(get("/api/v2/exercises/versions/{versionId}/preview", firstVersion).with(participant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseVersionId").value(firstVersion.toString()))
                .andExpect(jsonPath("$.movementPatterns[0]").value("SQUAT"))
                .andExpect(jsonPath("$.requiredEquipment[0]").value("BAND"));
    }

    private PublishedExercise createPublishedExercise(String canonicalName) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/admin/exercises").with(contentAdmin())
                        .contentType("application/json").content(createRequest(canonicalName)))
                .andExpect(status().isOk()).andReturn();
        JsonNode createdBody = json.readTree(created.getResponse().getContentAsString());
        UUID exerciseId = UUID.fromString(createdBody.path("exerciseId").asText());
        UUID versionId = UUID.fromString(createdBody.path("versionId").asText());

        MvcResult structure = mvc.perform(post("/api/v1/admin/anatomical-structures").with(contentAdmin())
                        .contentType("application/json").content("""
                                {"code":"SEARCH_%s","type":"MUSCLE_GROUP","displayName":"Search structure",
                                 "sidePolicy":"LEFT_RIGHT","taxonomyVersion":1}
                                """.formatted(UUID.randomUUID().toString().replace('-', '_'))))
                .andExpect(status().isOk()).andReturn();
        UUID structureId = UUID.fromString(json.readTree(structure.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post("/api/v1/admin/anatomical-structures/{id}/publish", structureId).with(contentAdmin()))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/exercises/versions/{id}/load-characteristics", versionId).with(contentAdmin())
                        .contentType("application/json").content("""
                                [{"movementPlane":"SAGITTAL","contractionType":"MIXED",
                                  "rangeOfMotion":"FULL","characteristicType":"DYNAMIC"}]
                                """))
                .andExpect(status().isOk());
        MvcResult evidence = mvc.perform(post("/api/v1/admin/exercises/versions/{id}/evidence", versionId).with(contentAdmin())
                        .contentType("application/json").content("""
                                {"citation":"Search evidence","sourceUri":"https://example.test/search",
                                 "evidenceGrade":"EDITORIAL_REVIEW"}
                                """))
                .andExpect(status().isOk()).andReturn();
        UUID evidenceId = UUID.fromString(json.readTree(evidence.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/contributions", versionId).with(contentAdmin())
                        .contentType("application/json").content("""
                                {"anatomicalStructureId":"%s","role":"PRIMARY","loadChannel":"DYN_EXU",
                                 "contributionBand":"HIGH","coefficientLow":0.2,"coefficientHigh":0.7,
                                 "confidenceClass":"MODERATE","evidenceGrade":"EDITORIAL_REVIEW",
                                 "calculationRole":"ALLOCATION","variantCondition":"STANDARD",
                                 "sideRule":"AS_PRESCRIBED","evidenceSourceIds":["%s"]}
                                """.formatted(structureId, evidenceId)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/submit-review", versionId).with(contentAdmin()))
                .andExpect(status().isOk());
        MvcResult approved = mvc.perform(post("/api/v1/admin/exercises/versions/{id}/approve", versionId).with(contentAdmin()))
                .andExpect(status().isOk()).andReturn();
        long expectedVersion = json.readTree(approved.getResponse().getContentAsString()).path("version").asLong();
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/publish", versionId).with(contentAdmin())
                        .contentType("application/json").content("{\"expectedVersion\":%d}".formatted(expectedVersion)))
                .andExpect(status().isOk());
        return new PublishedExercise(exerciseId, versionId);
    }

    private void persistAlias(UUID exerciseId, String alias) {
        transactions.executeWithoutResult(status -> entityManager.createNativeQuery("""
                INSERT INTO exercise_catalog.exercise_alias (id, exercise_id, locale, alias, normalized_alias)
                VALUES (:id, :exerciseId, 'pl-PL', :alias, :normalizedAlias)
                """).setParameter("id", UUID.randomUUID()).setParameter("exerciseId", exerciseId)
                .setParameter("alias", alias).setParameter("normalizedAlias", ExerciseCatalogSearchService.fold(alias))
                .executeUpdate());
    }

    private static String createRequest(String name) {
        return """
                {"canonicalName":"%s","version":{"instruction":"Controlled search movement",
                 "mediaReference":"s3://catalog/search.mp4","movementPatterns":["SQUAT"],
                 "stimulusType":"STRENGTH","fatigueProfile":"MODERATE","technicalLevel":"FOUNDATIONAL",
                 "environment":"ANY","requiredEquipment":["band"]}}
                """.formatted(name);
    }

    private static JwtRequestPostProcessor participant() {
        return jwt().jwt(builder -> builder.subject("search-participant").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
    }

    private static JwtRequestPostProcessor contentAdmin() {
        return jwt().jwt(builder -> builder.subject("search-admin").audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN"));
    }

    private record PublishedExercise(UUID exerciseId, UUID versionId) {
    }
}
