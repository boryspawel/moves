package com.motionecosystem.identityaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SubjectValidatorTest {

    private final SubjectValidator validator = new SubjectValidator();

    @Test
    void rejectsMissingSubject() {
        assertInvalidSubject(token(Map.of("aud", List.of("motion-api"))));
    }

    @Test
    void rejectsBlankSubject() {
        assertInvalidSubject(token(Map.of("sub", "   ")));
    }

    @Test
    void acceptsNonBlankSubject() {
        assertThat(validator.validate(token(Map.of("sub", "keycloak-subject"))).hasErrors()).isFalse();
    }

    private static Jwt token(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:05:00Z"),
                Map.of("alg", "none"),
                claims);
    }

    private void assertInvalidSubject(Jwt jwt) {
        var result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement()
                .extracting(error -> error.getErrorCode())
                .isEqualTo("invalid_token");
    }
}
