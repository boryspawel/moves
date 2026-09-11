package com.motionecosystem.identityaccess.api;

import java.util.Set;
import java.util.UUID;

/**
 * Neutral authorization boundary for consumers that need a specialist's
 * relationship and scoped professional capabilities.
 */
public interface SpecialistAuthorizationPort {

    void requireActiveRelationship(UUID specialistAccountId, UUID participantId);

    AuthorizationDecision requireCapabilities(
            UUID actorAccountId,
            UUID participantId,
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
        OVERRIDE_CLINICAL_BLOCK,
        VIEW_ADHERENCE_WORKLIST,
        RESPOND_TO_PARTICIPANT_ISSUE,
        MANAGE_PARTICIPANT_RECORDS
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
            UUID participantId,
            ProfessionalRole actingRole,
            Purpose purpose,
            Set<Capability> grantedCapabilities) {

        public AuthorizationDecision {
            grantedCapabilities = Set.copyOf(grantedCapabilities);
        }
    }
}
