package com.motionecosystem.specialist.api;

import java.util.Set;
import java.util.UUID;

public interface SpecialistAuthorizationPort {

    AuthorizationDecision requireCapabilities(
            UUID actorAccountId,
            UUID participantAccountId,
            ActingContext actingContext,
            Set<Capability> requiredCapabilities,
            Purpose purpose);

    enum Capability {
        PLAN_PERFORMANCE,
        PLAN_FUNCTIONAL_RECOVERY,
        SET_PERFORMANCE_BUDGET,
        SET_CLINICAL_RESTRICTION,
        VIEW_EFFECTIVE_RESTRICTION,
        VIEW_CLINICAL_RATIONALE,
        ACKNOWLEDGE_PERFORMANCE_WARNING,
        OVERRIDE_CLINICAL_BLOCK
    }

    enum ProfessionalRole {
        TRAINER,
        PHYSIOTHERAPIST
    }

    enum Purpose {
        PERFORMANCE_PLANNING,
        FUNCTIONAL_RECOVERY,
        CLINICAL_REVIEW
    }

    record ActingContext(ProfessionalRole role) {
    }

    record AuthorizationDecision(
            UUID actorAccountId,
            UUID participantAccountId,
            ProfessionalRole actingRole,
            Purpose purpose,
            Set<Capability> grantedCapabilities) {

        public AuthorizationDecision {
            grantedCapabilities = Set.copyOf(grantedCapabilities);
        }
    }
}
