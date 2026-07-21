package com.motionecosystem.specialist;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class SpecialistAuthorizationService implements SpecialistAuthorizationPort {
    private final ProfessionalScopeRepository scopes;
    private final ParticipantSpecialistRelationshipRepository relationships;
    private final ConsentDecisionPort consent;
    private final AuditRecorder audit;

    SpecialistAuthorizationService(ProfessionalScopeRepository scopes,
            ParticipantSpecialistRelationshipRepository relationships, ConsentDecisionPort consent,
            AuditRecorder audit) {
        this.scopes = scopes;
        this.relationships = relationships;
        this.consent = consent;
        this.audit = audit;
    }

    @Override
    public AuthorizationDecision requireCapabilities(UUID actor, UUID participant, ActingContext context,
            Set<Capability> required, Purpose purpose) {
        if (actor==null || participant==null || context==null || context.role()==null
                || required == null || required.isEmpty() || purpose == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "actor, participant, acting context, capabilities and purpose are required");
        }
        SpecialistKind kind = SpecialistKind.valueOf(context.role().name());
        if (!scopes.existsByIdAccountIdAndIdKindAndStatus(
                actor, kind, ProfessionalScope.VerificationStatus.VERIFIED)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "verified professional scope is required");
        }
        if (!relationships.existsBySpecialistAccountIdAndParticipantAccountIdAndStatus(
                actor, participant, ParticipantSpecialistRelationship.Status.ACTIVE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "active specialist relationship is required");
        }
        Set<Capability> granted = kind == SpecialistKind.TRAINER
                ? EnumSet.of(
                        Capability.PLAN_PERFORMANCE,
                        Capability.SET_PERFORMANCE_BUDGET,
                        Capability.VIEW_EFFECTIVE_RESTRICTION,
                        Capability.ACKNOWLEDGE_PERFORMANCE_WARNING)
                : EnumSet.of(
                        Capability.PLAN_FUNCTIONAL_RECOVERY,
                        Capability.SET_CLINICAL_RESTRICTION,
                        Capability.VIEW_EFFECTIVE_RESTRICTION,
                        Capability.VIEW_CLINICAL_RATIONALE,
                        Capability.OVERRIDE_CLINICAL_BLOCK);
        if (!granted.containsAll(required)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "required domain capability is missing");
        }
        Set<ConsentDecisionPort.DataScope> dataScopes =
                EnumSet.noneOf(ConsentDecisionPort.DataScope.class);
        if (required.stream().anyMatch(value -> value == Capability.VIEW_CLINICAL_RATIONALE
                || value == Capability.OVERRIDE_CLINICAL_BLOCK
                || value == Capability.SET_CLINICAL_RESTRICTION)) {
            dataScopes.add(ConsentDecisionPort.DataScope.CLINICAL_RATIONALE);
        }
        if (required.contains(Capability.VIEW_EFFECTIVE_RESTRICTION)) {
            dataScopes.add(ConsentDecisionPort.DataScope.EFFECTIVE_RESTRICTION);
        }
        if (required.stream().anyMatch(value -> value == Capability.PLAN_PERFORMANCE
                || value == Capability.PLAN_FUNCTIONAL_RECOVERY
                || value == Capability.SET_PERFORMANCE_BUDGET)) {
            dataScopes.add(ConsentDecisionPort.DataScope.PLAN);
        }
        if (!dataScopes.isEmpty()) {
            consent.requireAccess(
                    actor,
                    participant,
                    dataScopes,
                    ConsentDecisionPort.Purpose.valueOf(purpose.name()));
        }
        required.forEach(capability -> audit.record(
                actor.toString(),
                "CAPABILITY_" + context.role().name() + "_" + capability.name() + "_" + purpose.name(),
                "Participant",
                participant));
        return new AuthorizationDecision(actor, participant, context.role(), purpose, granted);
    }
}
