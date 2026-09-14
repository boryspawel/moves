package com.motionecosystem.specialist.api;

import com.motionecosystem.participant.api.ParticipantClientPort.RelationshipContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Neutral specialist-owned projection used by cross-module workspace composition. */
public interface SpecialistWorkspacePort {
    Optional<Profile> findProfile(UUID specialistId);
    void requireActiveRelationship(UUID specialistId, UUID participantId);
    Optional<Relationship> findRelationship(UUID specialistId, UUID participantId);
    Set<UUID> activeParticipantIds(UUID specialistId);
    void requireVerifiedScope(UUID specialistId, WorkspaceRole role);
    AuthorizationDecision requireParticipantCapabilities(UUID specialistId, UUID participantId, WorkspaceRole role,
            Set<WorkspaceCapability> requiredCapabilities, WorkspacePurpose purpose);
    void createActiveRelationship(UUID specialistId, UUID participantId, RelationshipContext context, Instant createdAt);
    Optional<ClientCreation> findClientCreation(UUID specialistId, UUID idempotencyKey);
    void saveClientCreation(UUID specialistId, UUID idempotencyKey, UUID participantId, String requestFingerprint, Instant createdAt);
    List<WorklistItem> listWorklist(String subject, WorkspaceRole role, WorkspacePurpose purpose);
    List<WorklistItem> listParticipantWorklist(String subject, UUID participantId, WorkspaceRole role, WorkspacePurpose purpose);

    enum WorkspaceRole { TRAINER, PHYSIOTHERAPIST }
    enum WorkspacePurpose { PERFORMANCE_PLANNING, FUNCTIONAL_RECOVERY, CLINICAL_REVIEW }
    enum WorkspaceCapability { PLAN_PERFORMANCE, PLAN_FUNCTIONAL_RECOVERY, VIEW_ADHERENCE_WORKLIST }
    record Profile(UUID specialistId, WorkspaceRole role, String timeZoneId) { }
    record Relationship(String status, Instant activatedAt) { }
    record AuthorizationDecision(WorkspaceRole role, WorkspacePurpose purpose,
                                 Set<String> grantedCapabilities) { }
    record ClientCreation(UUID participantId, String requestFingerprint) { }
    record WorklistItem(UUID id, UUID participantId, String category, String priority, String minimalData,
                        String status, Instant createdAt, Instant snoozedUntil) { }
}
