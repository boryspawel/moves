package com.motionecosystem.consent.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Test-only, account-free consent decision basis. It must never represent participant consent. */
public interface TestDefaultConsentOverridePort {
    java.util.Optional<OverrideDecision> find(UUID participantId, UUID specialistId,
                                              ConsentDecisionPort.Purpose purpose,
                                              Set<ConsentDecisionPort.DataScope> scopes);

    record OverrideDecision(UUID id, Instant createdAt) { }
}
