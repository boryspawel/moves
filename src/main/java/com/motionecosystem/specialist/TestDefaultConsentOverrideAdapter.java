package com.motionecosystem.specialist;

import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.consent.api.TestDefaultConsentOverridePort;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Exposes the explicitly audited test bridge to the consent decision owner. */
@Service
@RequiredArgsConstructor
class TestDefaultConsentOverrideAdapter implements TestDefaultConsentOverridePort {
    private final TestDefaultConsentOverrideRepository overrides;
    @Value("${moves.test-default-consent.enabled:false}") private boolean enabled;

    @Override
    public Optional<OverrideDecision> find(UUID participantId, UUID specialistId,
                                           ConsentDecisionPort.Purpose purpose,
                                           Set<ConsentDecisionPort.DataScope> scopes) {
        if (!enabled) return Optional.empty();
        Set<String> requested = scopes.stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return overrides.findByParticipantIdAndSpecialistIdAndPurpose(participantId, specialistId, purpose.name())
                .filter(item -> item.permits(purpose.name(), requested))
                .map(item -> new OverrideDecision(item.id(), item.createdAt));
    }
}
