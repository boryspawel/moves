package com.motionecosystem.identityaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakAuthoritiesConverterTest {

    @Test
    void mapsRealmAndClientRolesWithoutDuplicates() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:05:00Z"),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "subject",
                        "realm_access", Map.of("roles", List.of("PARTICIPANT")),
                        "resource_access", Map.of(
                                "motion-web", Map.of("roles", List.of("CONTENT_ADMIN", "PARTICIPANT"))
                        )
                ));

        assertThat(new KeycloakAuthoritiesConverter("motion-web").convert(jwt))
                .extracting(Object::toString)
                .containsExactly("ROLE_PARTICIPANT", "ROLE_CONTENT_ADMIN");
    }
}
