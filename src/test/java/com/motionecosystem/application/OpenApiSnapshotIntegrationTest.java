package com.motionecosystem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class OpenApiSnapshotIntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FilterChainProxy securityFilterChain;
    @Autowired
    ObjectMapper json;

    @Test
    void exposesAndOptionallySnapshotsTheRealContract() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        String contract = mvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        assertThat(contract).contains(
                "/api/v1/onboarding",
                "/api/v1/planned-sessions",
                "/api/v1/gamification/me",
                "/api/v1/specialist/participants",
                "/api/v1/specialist/available-slots",
                "/api/v1/specialist/worklist",
                "/api/v2/training-plans/{planId}/collaborators",
                "/api/v2/training-plans/{planId}/collaborators/{collaboratorId}",
                "/api/v2/training-plans/revisions/{revisionId}/reviews",
                "/api/v2/training-plans/reviews/{reviewId}/decision",
                "/api/v1/specialist/clients/{participantId}/measurements",
                "ParticipantMeasurementCommand",
                "ParticipantMeasurementView",
                "/api/v2/safety/participants/{participantId}/effective-restrictions",
                "/api/v2/safety/participants/{participantId}/clinical-restrictions",
                "/api/v2/safety/participants/{participantId}/restrictions/{restrictionId}");
        assertMeasurementContract(json.readTree(contract));

        String output = System.getProperty("openapi.snapshot");
        if (output != null && !output.isBlank()) {
            Path path = Path.of(output).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.writeString(path, json.writeValueAsString(canonicalize(json.readTree(contract))));
        }
    }

    private static void assertMeasurementContract(JsonNode contract) {
        JsonNode endpoint = contract.at("/paths/~1api~1v1~1specialist~1clients~1{participantId}~1measurements");
        JsonNode post = endpoint.path("post");
        assertThat(post.path("operationId").asText()).isEqualTo("recordParticipantMeasurement");
        assertThat(endpoint.path("get").path("operationId").asText()).isEqualTo("listParticipantMeasurements");
        assertThat(post.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ParticipantMeasurementCommand");
        assertThat(post.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ParticipantMeasurementView");
        assertThat(endpoint.at("/get/responses/200/content/*~1*/schema/items/$ref").asText())
                .isEqualTo("#/components/schemas/ParticipantMeasurementView");
        assertThat(post.path("parameters").findValues("name").stream().map(JsonNode::asText).toList()).contains("Idempotency-Key");
        JsonNode idempotency = post.path("parameters").findParents("name").stream()
                .filter(parameter -> "Idempotency-Key".equals(parameter.path("name").asText())).findFirst().orElseThrow();
        assertThat(idempotency.path("in").asText()).isEqualTo("header");
        assertThat(idempotency.path("required").asBoolean()).isTrue();
        JsonNode command = contract.at("/components/schemas/ParticipantMeasurementCommand");
        List<String> properties = new ArrayList<>();
        properties.addAll(command.path("properties").propertyNames());
        assertThat(properties).contains("presetId", "value", "unit", "measuredAt", "note",
                "bodyArea", "exercise", "activity", "customLabel");
        JsonNode preset = resolveSchema(contract, command.at("/properties/presetId"));
        List<String> presetValues = new ArrayList<>();
        preset.path("enum").forEach(value -> presetValues.add(value.asText()));
        assertThat(presetValues).containsExactly("BODY_WEIGHT", "BODY_CIRCUMFERENCE", "MAX_LOAD", "DISTANCE",
                "COMPLETION_TIME", "HOLD_DURATION", "CUSTOM");

        JsonNode measurementCatalog = contract.at("/paths/~1api~1v1~1specialist~1clients~1{participantId}~1measurements~1catalog/get");
        assertThat(measurementCatalog.path("operationId").asText()).isEqualTo("participantMeasurementCatalog");
        assertThat(measurementCatalog.at("/responses/200/content/*~1*/schema/items/$ref").asText())
                .isEqualTo("#/components/schemas/ParticipantMeasurementPresetView");

        JsonNode goalCatalog = contract.at("/paths/~1api~1v1~1specialist~1clients~1{participantId}~1goals~1catalog/get");
        JsonNode goalPreset = resolveSchema(contract, goalCatalog.at("/responses/200/content/*~1*/schema/items"));
        List<String> goalProperties = new ArrayList<>();
        goalPreset.path("properties").propertyNames().forEach(goalProperties::add);
        assertThat(goalProperties).contains("defaultComparator", "comparatorSelectable", "baselineSupported");
    }

    private static JsonNode resolveSchema(JsonNode contract, JsonNode schema) {
        String ref = schema.path("$ref").asText();
        return ref.isBlank() ? schema : contract.at("/components/schemas/" + ref.substring("#/components/schemas/".length()));
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = json.createObjectNode();
            node.propertyNames().stream().sorted()
                    .forEach(name -> result.set(name, canonicalize(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = json.createArrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node.deepCopy();
    }
}
