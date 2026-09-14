package com.motionecosystem.specialist;

import com.motionecosystem.participant.api.ParticipantClientPort.RelationshipContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class SpecialistWorkspaceAdapter implements SpecialistWorkspacePort {
    private final SpecialistProfileService profiles;
    private final SpecialistRelationshipService relationships;
    private final SpecialistAuthorizationService authorization;
    private final ParticipantSpecialistRelationshipRepository relationshipRepository;
    private final ClientCreateIdempotencyRepository clientCreations;
    private final SpecialistWorklistService worklist;

    @Override public Optional<Profile> findProfile(UUID specialistId) {
        return profiles.find(specialistId).map(value -> new Profile(specialistId, role(value.specialistKind()), value.timeZoneId()));
    }
    @Override public void requireActiveRelationship(UUID specialistId, UUID participantId) { relationships.requireActive(specialistId, participantId); }
    @Override public Optional<Relationship> findRelationship(UUID specialistId, UUID participantId) {
        return relationshipRepository.findBySpecialistAccountIdAndParticipantId(specialistId, participantId)
                .map(value -> new Relationship(value.status().name(), value.activatedAt()));
    }
    @Override public Set<UUID> activeParticipantIds(UUID specialistId) { return relationships.activeParticipantIds(specialistId); }
    @Override public void requireVerifiedScope(UUID specialistId, WorkspaceRole role) { authorization.requireVerifiedScope(specialistId, kind(role)); }
    @Override public AuthorizationDecision requireParticipantCapabilities(UUID specialistId, UUID participantId,
            WorkspaceRole role, Set<WorkspaceCapability> requiredCapabilities, WorkspacePurpose purpose) {
        var decision = authorization.requireCapabilities(specialistId, participantId, new ActingContext(professionalRole(role)),
                requiredCapabilities.stream().map(SpecialistWorkspaceAdapter::capability).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                purpose(purpose));
        return new AuthorizationDecision(role(decision.actingRole()), purpose(decision.purpose()),
                decision.grantedCapabilities().stream().map(Enum::name)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
    @Override public void createActiveRelationship(UUID specialistId, UUID participantId, RelationshipContext context, Instant createdAt) {
        relationshipRepository.save(new ParticipantSpecialistRelationship(specialistId, participantId, context, createdAt));
    }
    @Override public Optional<ClientCreation> findClientCreation(UUID specialistId, UUID idempotencyKey) {
        return clientCreations.findById(new ClientCreateIdempotency.Id(specialistId, idempotencyKey))
                .map(value -> new ClientCreation(value.participantId(), value.requestFingerprint()));
    }
    @Override public void saveClientCreation(UUID specialistId, UUID idempotencyKey, UUID participantId, String requestFingerprint, Instant createdAt) {
        clientCreations.saveAndFlush(new ClientCreateIdempotency(specialistId, idempotencyKey, participantId, requestFingerprint, createdAt));
    }
    @Override public List<WorklistItem> listWorklist(String subject, WorkspaceRole role, WorkspacePurpose purpose) {
        return worklist.list(subject, new ActingContext(professionalRole(role)), purpose(purpose)).stream().map(SpecialistWorkspaceAdapter::item).toList();
    }
    @Override public List<WorklistItem> listParticipantWorklist(String subject, UUID participantId, WorkspaceRole role, WorkspacePurpose purpose) {
        return worklist.forParticipant(subject, participantId, new ActingContext(professionalRole(role)), purpose(purpose)).stream().map(SpecialistWorkspaceAdapter::item).toList();
    }
    private static WorklistItem item(SpecialistWorklistService.WorklistItemView value) { return new WorklistItem(value.id(), value.participantId(), value.category(), value.priority(), value.minimalData(), value.status(), value.createdAt(), value.snoozedUntil()); }
    private static WorkspaceRole role(SpecialistKind value) { return value == SpecialistKind.TRAINER ? WorkspaceRole.TRAINER : WorkspaceRole.PHYSIOTHERAPIST; }
    private static WorkspaceRole role(ProfessionalRole value) { return value == ProfessionalRole.TRAINER ? WorkspaceRole.TRAINER : WorkspaceRole.PHYSIOTHERAPIST; }
    private static SpecialistKind kind(WorkspaceRole value) { return value == WorkspaceRole.TRAINER ? SpecialistKind.TRAINER : SpecialistKind.PHYSIOTHERAPIST; }
    private static ProfessionalRole professionalRole(WorkspaceRole value) { return value == WorkspaceRole.TRAINER ? ProfessionalRole.TRAINER : ProfessionalRole.PHYSIOTHERAPIST; }
    private static Purpose purpose(WorkspacePurpose value) { return switch (value) { case PERFORMANCE_PLANNING -> Purpose.PERFORMANCE_PLANNING; case FUNCTIONAL_RECOVERY -> Purpose.FUNCTIONAL_RECOVERY; case CLINICAL_REVIEW -> Purpose.CLINICAL_REVIEW; }; }
    private static WorkspacePurpose purpose(Purpose value) { return switch (value) { case PERFORMANCE_PLANNING -> WorkspacePurpose.PERFORMANCE_PLANNING; case FUNCTIONAL_RECOVERY -> WorkspacePurpose.FUNCTIONAL_RECOVERY; case CLINICAL_REVIEW -> WorkspacePurpose.CLINICAL_REVIEW; }; }
    private static Capability capability(WorkspaceCapability value) { return switch (value) { case PLAN_PERFORMANCE -> Capability.PLAN_PERFORMANCE; case PLAN_FUNCTIONAL_RECOVERY -> Capability.PLAN_FUNCTIONAL_RECOVERY; case VIEW_ADHERENCE_WORKLIST -> Capability.VIEW_ADHERENCE_WORKLIST; }; }
}
