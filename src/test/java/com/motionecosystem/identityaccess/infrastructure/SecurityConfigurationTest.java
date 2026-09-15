package com.motionecosystem.identityaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigurationTest {

    @Test
    void centralDecoderValidatorRejectsAnOtherwiseValidTokenWithoutSubject() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of(
                        "iss", "https://issuer.example/realms/motion",
                        "aud", List.of("motion-api")));

        var result = new SecurityConfiguration()
                .jwtValidator("https://issuer.example/realms/motion", "motion-api")
                .validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(error ->
                assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }
}
