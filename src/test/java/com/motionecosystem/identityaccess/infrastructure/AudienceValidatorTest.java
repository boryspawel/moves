package com.motionecosystem.identityaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    @Test
    void requiresConfiguredAudience() {
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:05:00Z"),
                Map.of("alg", "none"),
                Map.of("sub", "subject", "aud", List.of("different-api")));

        assertThat(new AudienceValidator("motion-api").validate(jwt).hasErrors()).isTrue();
    }
}
