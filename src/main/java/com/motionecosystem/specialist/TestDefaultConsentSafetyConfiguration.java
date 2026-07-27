package com.motionecosystem.specialist;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import jakarta.annotation.PostConstruct;

/** Prevents a test-only access basis from ever being enabled in production. */
@Configuration
class TestDefaultConsentSafetyConfiguration {
    private final Environment environment;
    private final boolean enabled;
    TestDefaultConsentSafetyConfiguration(Environment environment, @Value("${moves.test-default-consent.enabled:false}") boolean enabled) { this.environment = environment; this.enabled = enabled; }
    @PostConstruct void rejectProductionOverride() {
        boolean production = Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> "prod".equalsIgnoreCase(profile));
        boolean allowedNonProductionProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> "local".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
        if (enabled && (production || !allowedNonProductionProfile)) {
            throw new IllegalStateException("moves.test-default-consent.enabled is allowed only in local or test profiles");
        }
    }
}
