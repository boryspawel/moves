package com.motionecosystem.consent;

import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.consent.api.TestDefaultConsentOverridePort;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Consent-owned, explicitly audited bridge for account-free local and test fixtures. */
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

    @Override
    public void create(CreateCommand command) {
        if (!enabled) throw new IllegalStateException("test default consent override is disabled");
        overrides.save(new TestDefaultConsentOverride(command.participantId(), command.specialistId(), command.purpose().name(),
                command.scopes().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(",")),
                command.createdByAccountId().toString(), command.createdAt()));
    }
}
