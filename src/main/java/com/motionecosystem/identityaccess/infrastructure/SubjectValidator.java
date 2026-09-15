package com.motionecosystem.identityaccess.infrastructure;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Resource APIs use the OIDC subject as the stable external account reference.
 * A token without one must never reach application services.
 */
final class SubjectValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SUBJECT = new OAuth2Error(
            "invalid_token", "Required subject is missing", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        return subject != null && !subject.isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
    }
}
