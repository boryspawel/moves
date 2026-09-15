package com.motionecosystem.identityaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class KeycloakRealmConfigurationTest {

    @Test
    void motionWebReceivesSubjectFromConcreteBasicScope() throws Exception {
        JsonNode realm = new ObjectMapper().readTree(Files.readString(
                Path.of("infra/keycloak/motion-local-realm.json")));

        JsonNode motionWeb = stream(realm.path("clients"))
                .filter(client -> "motion-web".equals(client.path("clientId").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(stream(motionWeb.path("defaultClientScopes"))
                .map(JsonNode::asText)).contains("basic");

        JsonNode basic = stream(realm.path("clientScopes"))
                .filter(scope -> "basic".equals(scope.path("name").asText()))
                .findFirst()
                .orElseThrow();
        JsonNode mapper = stream(basic.path("protocolMappers"))
                .filter(candidate -> "oidc-sub-mapper".equals(candidate.path("protocolMapper").asText()))
                .findFirst()
                .orElseThrow();

        assertThat(mapper.path("config").path("access.token.claim").asText()).isEqualTo("true");
        assertThat(mapper.path("config").path("introspection.token.claim").asText()).isEqualTo("true");
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false);
    }
}
