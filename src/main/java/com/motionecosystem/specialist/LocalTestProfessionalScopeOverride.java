package com.motionecosystem.specialist;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicitly non-persistent local/test authorization basis for demonstration specialists. */
@Component
@RequiredArgsConstructor
class LocalTestProfessionalScopeOverride {
    private static final Logger log = LoggerFactory.getLogger(LocalTestProfessionalScopeOverride.class);

    private final Environment environment;
    private final SpecialistProfileRepository profiles;
    @Value("${moves.local-test-verified-scope.enabled:false}")
    private boolean enabled;

    @PostConstruct
    void rejectUnsafeConfiguration() {
        if (!enabled) return;
        boolean production = hasProfile("prod");
        boolean localOrTest = hasProfile("local") || hasProfile("test");
        if (production || !localOrTest) {
            throw new IllegalStateException("moves.local-test-verified-scope.enabled is allowed only in local or test profiles");
        }
        log.warn("Local/test verified professional-scope override is enabled; no verification is persisted");
    }

    boolean permits(UUID specialistAccountId, SpecialistKind kind) {
        return enabled && profiles.findByAccountId(specialistAccountId)
                .map(profile -> profile.specialistKind == kind)
                .orElse(false);
    }

    private boolean hasProfile(String expected) {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(expected::equalsIgnoreCase);
    }
}
