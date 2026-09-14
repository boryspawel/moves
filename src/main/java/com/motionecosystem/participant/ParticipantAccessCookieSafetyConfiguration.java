package com.motionecosystem.participant;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import jakarta.annotation.PostConstruct;

/** Secure cookies may be disabled only by the explicitly non-production local/test transport. */
@Configuration
class ParticipantAccessCookieSafetyConfiguration {
    private final Environment environment;
    private final boolean secure;
    ParticipantAccessCookieSafetyConfiguration(Environment environment,
            @Value("${participant-access.cookie-secure:true}") boolean secure) { this.environment = environment; this.secure = secure; }
    @PostConstruct void rejectUnsafeProductionCookie() {
        if (!secure && Arrays.stream(environment.getActiveProfiles()).noneMatch(p -> "local".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)))
            throw new IllegalStateException("participant-access.cookie-secure=false is allowed only in local or test profiles");
    }
}
