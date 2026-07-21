package com.motionecosystem.consent.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface ConsentDecisionPort {

    ConsentDecision requireAccess(
            UUID actorAccountId,
            UUID participantAccountId,
            Set<DataScope> dataScopes,
            Purpose purpose);

    enum DataScope {
        PLAN,
        EXECUTION,
        EFFECTIVE_RESTRICTION,
        CLINICAL_RATIONALE
    }

    enum Purpose {
        PERFORMANCE_PLANNING,
        FUNCTIONAL_RECOVERY,
        CLINICAL_REVIEW
    }

    record ConsentDecision(
            UUID grantId,
            UUID actorAccountId,
            UUID participantAccountId,
            Set<DataScope> dataScopes,
            Purpose purpose,
            Instant validUntil) {

        public ConsentDecision {
            dataScopes = Set.copyOf(dataScopes);
        }
    }
}
