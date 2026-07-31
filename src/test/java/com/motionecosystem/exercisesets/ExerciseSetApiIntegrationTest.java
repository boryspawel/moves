package com.motionecosystem.exercisesets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.support.PostgresTestConfiguration;
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
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class ExerciseSetApiIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy securityFilterChain;
    @Autowired ObjectMapper json;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    @Test
    void migratesSchemaAndExposesOwnerScopedDraftLifecycleWithoutParticipantOrSessionData() throws Exception {
        activateSpecialist("exercise-set-owner");
        MvcResult create = mvc.perform(post("/api/v1/specialist/exercise-sets").with(specialist("exercise-set-owner")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerAccountId").exists())
                .andExpect(jsonPath("$.participantId").doesNotExist())
                .andExpect(jsonPath("$.versions[0].status").value("DRAFT"))
                .andReturn();
        JsonNode body = json.readTree(create.getResponse().getContentAsString());
        UUID setId = UUID.fromString(body.path("id").asText());
        UUID draftId = UUID.fromString(body.path("versions").get(0).path("id").asText());

        mvc.perform(put("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", setId, draftId)
                        .with(specialist("exercise-set-owner")).contentType("application/json")
                        .content("{\"profile\":\"HOME\",\"title\":\"Home mobility\",\"tags\":[],\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseSetId").value(setId.toString()))
                .andExpect(jsonPath("$.participantAccountId").doesNotExist());

        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", setId, draftId)
                        .with(specialist("exercise-set-owner")).contentType("application/json")
                        .content(publishRequest(version("exercise-set-owner", new SetIds(setId, draftId)).path("lockVersion").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.analysis.status").value("SUGGESTIONS_AVAILABLE"));

        activateSpecialist("exercise-set-other-specialist");
        mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}", setId)
                        .with(specialist("exercise-set-other-specialist")))
                .andExpect(status().isForbidden());
    }

    @Test
    void persistsAndReadsEveryTypedDoseWithPublishedCatalogSnapshot() throws Exception {
        String subject = "exercise-set-dose-owner";
        activateSpecialist(subject);
        String exerciseName = "Dose exercise " + UUID.randomUUID();
        UUID publishedExercise = createPublishedExercise(exerciseName);
        SetIds ids = createDraft(subject, "Typed doses");

        for (String dose : List.of(
                "{\"type\":\"STRENGTH\",\"sets\":3,\"reps\":8,\"restSeconds\":60,\"side\":\"BILATERAL\"}",
                "{\"type\":\"ISOMETRIC\",\"sets\":2,\"holdSeconds\":20,\"restSeconds\":30,\"side\":\"LEFT\"}",
                "{\"type\":\"MOBILITY\",\"reps\":8,\"rangeTarget\":\"COMFORTABLE\",\"side\":\"RIGHT\"}",
                "{\"type\":\"STRETCH\",\"holdSeconds\":30,\"repetitions\":2,\"side\":\"LEFT\",\"intensity\":\"GENTLE\"}",
                "{\"type\":\"BREATHING\",\"durationSeconds\":120,\"rhythm\":\"4-4\"}",
                "{\"type\":\"AEROBIC\",\"durationSeconds\":600,\"distanceMeters\":1000,\"intensity\":\"EASY\",\"zone\":\"Z2\",\"rpe\":4}")) {
            addItem(subject, ids, publishedExercise, dose);
        }

        mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", ids.setId(), ids.versionId())
                        .with(specialist(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[0].dose.type").value("STRENGTH"))
                .andExpect(jsonPath("$.items[1].dose.type").value("ISOMETRIC"))
                .andExpect(jsonPath("$.items[2].dose.type").value("MOBILITY"))
                .andExpect(jsonPath("$.items[3].dose.type").value("STRETCH"))
                .andExpect(jsonPath("$.items[4].dose.type").value("BREATHING"))
                .andExpect(jsonPath("$.items[5].dose.type").value("AEROBIC"))
                .andExpect(jsonPath("$.items[0].snapshot.canonicalName").value(exerciseName));
        assertThat(version(subject, ids).path("items")).extracting(node -> node.path("position").asInt())
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void movesDraftItemsTransactionallyAndKeepsPositionsContiguous() throws Exception {
        String subject = "exercise-set-move-owner";
        activateSpecialist(subject);
        UUID publishedExercise = createPublishedExercise("Move exercise " + UUID.randomUUID());
        SetIds ids = createDraft(subject, "Move routine");
        addItem(subject, ids, publishedExercise, strengthDose());
        addItem(subject, ids, publishedExercise, strengthDose());
        addItem(subject, ids, publishedExercise, strengthDose());
        JsonNode before = version(subject, ids);
        UUID last = UUID.fromString(before.path("items").get(2).path("id").asText());

        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items/move", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json")
                        .content("{\"itemId\":\"%s\",\"targetPosition\":1,\"expectedVersion\":%d}"
                                .formatted(last, before.path("lockVersion").asLong())))
                .andExpect(status().isOk());

        JsonNode moved = version(subject, ids);
        assertThat(moved.path("items").get(0).path("id").asText()).isEqualTo(last.toString());
        assertThat(moved.path("items")).extracting(node -> node.path("position").asInt())
                .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsStaleDraftWriteAndUnpublishedCatalogReference() throws Exception {
        String subject = "exercise-set-concurrent-owner";
        activateSpecialist(subject);
        SetIds ids = createDraft(subject, "Concurrency routine");
        JsonNode current = version(subject, ids);
        long token = current.path("lockVersion").asLong();
        String metadata = "{\"profile\":\"HOME\",\"title\":\"Concurrency routine changed\",\"tags\":[],\"expectedVersion\":%d}".formatted(token);
        mvc.perform(put("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json").content(metadata))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json").content(metadata))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"));

        UUID unpublished = createCatalogDraft("Unpublished exercise " + UUID.randomUUID());
        JsonNode refreshed = version(subject, ids);
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json")
                        .content(itemRequest(unpublished, strengthDose(), refreshed.path("lockVersion").asLong())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXERCISE_VERSION_NOT_PUBLISHED"));
    }

    @Test
    void chainsDraftMutationsFromReturnedLockTokensWithoutAnInterveningRead() throws Exception {
        String subject = "exercise-set-response-token-owner";
        activateSpecialist(subject);
        UUID publishedExercise = createPublishedExercise("Response token exercise " + UUID.randomUUID());
        MvcResult created = mvc.perform(post("/api/v1/specialist/exercise-sets").with(specialist(subject)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode set = json.readTree(created.getResponse().getContentAsString());
        SetIds ids = new SetIds(UUID.fromString(set.path("id").asText()), UUID.fromString(set.path("versions").get(0).path("id").asText()));

        JsonNode metadata = response(mvc.perform(put("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", ids.setId(), ids.versionId())
                .with(specialist(subject)).contentType("application/json")
                .content("{\"profile\":\"HOME\",\"title\":\"Response token routine\",\"tags\":[],\"expectedVersion\":0}")));
        JsonNode strength = response(mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items", ids.setId(), ids.versionId())
                .with(specialist(subject)).contentType("application/json")
                .content(itemRequest(publishedExercise, strengthDose(), metadata.path("lockVersion").asLong()))));
        JsonNode mobility = response(mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items", ids.setId(), ids.versionId())
                .with(specialist(subject)).contentType("application/json")
                .content(itemRequest(publishedExercise, "{\"type\":\"MOBILITY\",\"reps\":8,\"rangeTarget\":\"COMFORTABLE\",\"side\":\"RIGHT\"}", strength.path("lockVersion").asLong()))));
        UUID firstItemId = UUID.fromString(mobility.path("items").get(0).path("id").asText());
        JsonNode updated = response(mvc.perform(put("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items/{itemId}", ids.setId(), ids.versionId(), firstItemId)
                .with(specialist(subject)).contentType("application/json")
                .content(itemRequest(publishedExercise, "{\"type\":\"MOBILITY\",\"durationSeconds\":30,\"rangeTarget\":\"COMFORTABLE\",\"side\":\"RIGHT\"}", mobility.path("lockVersion").asLong()))));
        UUID secondItemId = UUID.fromString(updated.path("items").get(1).path("id").asText());
        JsonNode moved = response(mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items/move", ids.setId(), ids.versionId())
                .with(specialist(subject)).contentType("application/json")
                .content("{\"itemId\":\"%s\",\"targetPosition\":1,\"expectedVersion\":%d}".formatted(secondItemId, updated.path("lockVersion").asLong()))));
        JsonNode removed = response(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items/{itemId}", ids.setId(), ids.versionId(), secondItemId)
                .with(specialist(subject)).param("expectedVersion", Long.toString(moved.path("lockVersion").asLong()))));
        JsonNode added = response(mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items", ids.setId(), ids.versionId())
                .with(specialist(subject)).contentType("application/json")
                .content(itemRequest(publishedExercise, strengthDose(), removed.path("lockVersion").asLong()))));
        JsonNode published = response(mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", ids.setId(), ids.versionId())
                .with(specialist(subject)).contentType("application/json")
                .content(publishRequest(added.path("lockVersion").asLong()))));

        assertThat(List.of(metadata, strength, mobility, updated, moved, removed, added, published))
                .extracting(node -> node.path("lockVersion").asLong())
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        JsonNode persisted = version(subject, ids);
        assertThat(persisted.path("lockVersion").asLong()).isEqualTo(published.path("lockVersion").asLong());
        assertThat(persisted.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(persisted.path("items")).hasSize(2);
        assertThat(persisted.path("items").get(1).path("dose").path("type").asText()).isEqualTo("STRENGTH");
    }

    @Test
    void publishedVersionIsImmutableRetirableAndHistoricallyReadable() throws Exception {
        String subject = "exercise-set-history-owner";
        activateSpecialist(subject);
        UUID publishedExercise = createPublishedExercise("History exercise " + UUID.randomUUID());
        SetIds ids = createDraft(subject, "History routine");
        addItem(subject, ids, publishedExercise, strengthDose());
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json")
                        .content(publishRequest(version(subject, ids).path("lockVersion").asLong())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        UUID itemId = UUID.fromString(version(subject, ids).path("items").get(0).path("id").asText());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items/{itemId}", ids.setId(), ids.versionId(), itemId)
                        .with(specialist(subject)).param("expectedVersion", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERSION_NOT_EDITABLE"));
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/retire", ids.setId(), ids.versionId())
                        .with(specialist(subject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETIRED"));
        mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", ids.setId(), ids.versionId())
                        .with(specialist(subject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETIRED"))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/analysis", ids.setId(), ids.versionId())
                        .with(specialist(subject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
    }

    @Test
    void publishRequiresExpectedVersionAndRejectsStaleConcurrentPublish() throws Exception {
        String subject = "exercise-set-publish-concurrency-owner";
        activateSpecialist(subject);
        UUID publishedExercise = createPublishedExercise("Publish concurrency exercise " + UUID.randomUUID());
        SetIds ids = createDraft(subject, "Publish concurrency routine");
        addItem(subject, ids, publishedExercise, strengthDose());
        long expectedVersion = version(subject, ids).path("lockVersion").asLong();

        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", ids.setId(), ids.versionId())
                        .with(specialist(subject)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json").content(publishRequest(expectedVersion)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json").content(publishRequest(expectedVersion)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"));
    }

    @Test
    void analysisIsOwnerScopedAndDraftAnalysisUsesCurrentLockVersion() throws Exception {
        String owner = "exercise-set-analysis-owner";
        activateSpecialist(owner);
        UUID publishedExercise = createPublishedExercise("Analysis exercise " + UUID.randomUUID());
        SetIds ids = createDraft(owner, "Analysis routine");
        addItem(owner, ids, publishedExercise, strengthDose());
        long lockVersion = version(owner, ids).path("lockVersion").asLong();

        mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/analysis", ids.setId(), ids.versionId()).with(specialist(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draft").value(true))
                .andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.analyzedLockVersion").value(lockVersion));
        activateSpecialist("exercise-set-analysis-other");
        mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/analysis", ids.setId(), ids.versionId()).with(specialist("exercise-set-analysis-other")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SET_ACCESS_DENIED"));
    }

    @Test
    void publishStoresImmutableAnalysisWithoutBlockingOnSuggestions() throws Exception {
        String subject = "exercise-set-analysis-publish";
        activateSpecialist(subject);
        UUID publishedExercise = createPublishedExercise("Analysis publish " + UUID.randomUUID());
        SetIds blocked = createDraft(subject, "Blocked routine");
        JsonNode before = version(subject, blocked);
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items", blocked.setId(), blocked.versionId()).with(specialist(subject)).contentType("application/json")
                        .content(itemRequest(publishedExercise, strengthDose().replace("\"phase\":", "\"phase\":"), before.path("lockVersion").asLong())))
                .andExpect(status().isOk());
        long afterItem = version(subject, blocked).path("lockVersion").asLong();
        // A MAIN-only full set is published with advisory suggestions.
        mvc.perform(put("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", blocked.setId(), blocked.versionId()).with(specialist(subject)).contentType("application/json")
                        .content("{\"profile\":\"FULL_SELF_GUIDED\",\"title\":\"Blocked routine\",\"tags\":[],\"expectedVersion\":%d}".formatted(afterItem)))
                .andExpect(status().isOk());
        long blockedLock = version(subject, blocked).path("lockVersion").asLong();
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", blocked.setId(), blocked.versionId()).with(specialist(subject)).contentType("application/json")
                        .content(publishRequest(blockedLock))).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.status").value("SUGGESTIONS_AVAILABLE"));

        SetIds ids = createDraft(subject, "Published analysis");
        addItem(subject, ids, publishedExercise, strengthDose());
        long expected = version(subject, ids).path("lockVersion").asLong();
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/publish", ids.setId(), ids.versionId()).with(specialist(subject)).contentType("application/json")
                        .content(publishRequest(expected))).andExpect(status().isOk()).andExpect(jsonPath("$.analysis.published").value(true))
                .andExpect(jsonPath("$.analysis.policyVersion").value("exercise-set-policy-v1"));
        mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/analysis", ids.setId(), ids.versionId()).with(specialist(subject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draft").value(false)).andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.metrics.itemCount").value(1));
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/next-draft", ids.setId(), ids.versionId()).with(specialist(subject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analysis").doesNotExist());
    }

    private SetIds createDraft(String subject, String title) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/specialist/exercise-sets").with(specialist(subject)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode set = json.readTree(created.getResponse().getContentAsString());
        SetIds ids = new SetIds(UUID.fromString(set.path("id").asText()), UUID.fromString(set.path("versions").get(0).path("id").asText()));
        mvc.perform(put("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json")
                        .content("{\"profile\":\"HOME\",\"title\":\"%s\",\"tags\":[],\"expectedVersion\":0}".formatted(title)))
                .andExpect(status().isOk());
        return ids;
    }

    private void addItem(String subject, SetIds ids, UUID exerciseVersionId, String dose) throws Exception {
        JsonNode latest = version(subject, ids);
        mvc.perform(post("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/items", ids.setId(), ids.versionId())
                        .with(specialist(subject)).contentType("application/json")
                        .content(itemRequest(exerciseVersionId, dose, latest.path("lockVersion").asLong())))
                .andExpect(status().isOk());
    }

    private JsonNode version(String subject, SetIds ids) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/specialist/exercise-sets/{setId}/versions/{versionId}", ids.setId(), ids.versionId())
                        .with(specialist(subject))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode response(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UUID createPublishedExercise(String canonicalName) throws Exception {
        UUID versionId = createCatalogDraft(canonicalName);
        MvcResult structure = mvc.perform(post("/api/v1/admin/anatomical-structures").with(contentAdmin())
                        .contentType("application/json").content("{\"code\":\"STRUCT_%s\",\"type\":\"MUSCLE_GROUP\",\"displayName\":\"Test structure\",\"sidePolicy\":\"LEFT_RIGHT\",\"taxonomyVersion\":1}".formatted(UUID.randomUUID().toString().replace('-', '_'))))
                .andExpect(status().isOk()).andReturn();
        UUID structureId = UUID.fromString(json.readTree(structure.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post("/api/v1/admin/anatomical-structures/{id}/publish", structureId).with(contentAdmin())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/exercises/versions/{id}/load-characteristics", versionId).with(contentAdmin())
                        .contentType("application/json").content("[{\"movementPlane\":\"SAGITTAL\",\"contractionType\":\"MIXED\",\"rangeOfMotion\":\"FULL\",\"characteristicType\":\"DYNAMIC\"}]"))
                .andExpect(status().isOk());
        MvcResult evidence = mvc.perform(post("/api/v1/admin/exercises/versions/{id}/evidence", versionId).with(contentAdmin())
                        .contentType("application/json").content("{\"citation\":\"Test evidence\",\"sourceUri\":\"https://example.test/evidence\",\"evidenceGrade\":\"EDITORIAL_REVIEW\"}"))
                .andExpect(status().isOk()).andReturn();
        UUID evidenceId = UUID.fromString(json.readTree(evidence.getResponse().getContentAsString()).path("id").asText());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/contributions", versionId).with(contentAdmin())
                        .contentType("application/json").content("{\"anatomicalStructureId\":\"%s\",\"role\":\"PRIMARY\",\"loadChannel\":\"DYN_EXU\",\"contributionBand\":\"HIGH\",\"coefficientLow\":0.2,\"coefficientHigh\":0.7,\"confidenceClass\":\"MODERATE\",\"evidenceGrade\":\"EDITORIAL_REVIEW\",\"calculationRole\":\"ALLOCATION\",\"variantCondition\":\"STANDARD\",\"sideRule\":\"AS_PRESCRIBED\",\"evidenceSourceIds\":[\"%s\"]}".formatted(structureId, evidenceId)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/submit-review", versionId).with(contentAdmin())).andExpect(status().isOk());
        MvcResult approved = mvc.perform(post("/api/v1/admin/exercises/versions/{id}/approve", versionId).with(contentAdmin()))
                .andExpect(status().isOk()).andReturn();
        long expected = json.readTree(approved.getResponse().getContentAsString()).path("version").asLong();
        mvc.perform(post("/api/v1/admin/exercises/versions/{id}/publish", versionId).with(contentAdmin())
                        .contentType("application/json").content("{\"expectedVersion\":%d}".formatted(expected)))
                .andExpect(status().isOk());
        return versionId;
    }

    private UUID createCatalogDraft(String canonicalName) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/exercises").with(contentAdmin()).contentType("application/json")
                        .content("{\"canonicalName\":\"%s\",\"version\":{\"instruction\":\"Controlled movement\",\"mediaReference\":\"s3://catalog/test.mp4\",\"movementPatterns\":[\"SQUAT\"],\"stimulusType\":\"STRENGTH\",\"fatigueProfile\":\"MODERATE\",\"technicalLevel\":\"FOUNDATIONAL\",\"environment\":\"ANY\",\"requiredEquipment\":[\"band\"]}}".formatted(canonicalName)))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("versionId").asText());
    }

    private static String itemRequest(UUID exerciseVersionId, String dose, long expectedVersion) {
        return "{\"exerciseVersionId\":\"%s\",\"phase\":\"MAIN\",\"dose\":%s,\"expectedVersion\":%d}".formatted(exerciseVersionId, dose, expectedVersion);
    }

    private static String publishRequest(long expectedVersion) { return "{\"expectedVersion\":%d}".formatted(expectedVersion); }

    private static String strengthDose() { return "{\"type\":\"STRENGTH\",\"sets\":3,\"reps\":8,\"restSeconds\":60,\"side\":\"BILATERAL\"}"; }
    private static JwtRequestPostProcessor contentAdmin() { return jwt().jwt(builder -> builder.subject("exercise-set-content-admin").audience(List.of("motion-api"))).authorities(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN")); }
    private record SetIds(UUID setId, UUID versionId) { }

    private void activateSpecialist(String subject) throws Exception {
        mvc.perform(put("/api/v1/onboarding/profile-type").with(specialist(subject))
                        .contentType("application/json").content("{\"profileType\":\"SPECIALIST\"}"))
                .andExpect(status().isOk());
    }

    private static JwtRequestPostProcessor specialist(String subject) {
        return jwt().jwt(builder -> builder.subject(subject).audience(List.of("motion-api")))
                .authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST"));
    }
}
