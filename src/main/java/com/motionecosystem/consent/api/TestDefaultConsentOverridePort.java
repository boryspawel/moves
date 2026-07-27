package com.motionecosystem.consent.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Test-only, account-free consent decision basis. It must never represent participant consent. */
public interface TestDefaultConsentOverridePort {
    java.util.Optional<OverrideDecision> find(UUID participantId, UUID specialistId,
                                              ConsentDecisionPort.Purpose purpose,
                                              Set<ConsentDecisionPort.DataScope> scopes);

    /** Records the explicitly non-participant test decision in the consent boundary. */
    void create(CreateCommand command);

    record OverrideDecision(UUID id, Instant createdAt) { }

    record CreateCommand(UUID participantId, UUID specialistId, ConsentDecisionPort.Purpose purpose,
                         Set<ConsentDecisionPort.DataScope> scopes, UUID createdByAccountId, Instant createdAt) { }
}
