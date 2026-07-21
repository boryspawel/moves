package com.motionecosystem.trainingplanning.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface PlanRevisionWorkflowPersistence {

    WorkflowState state(UUID revisionId);

    WorkflowState completeValidation(
            UUID revisionId,
            long expectedVersion,
            String checksum,
            UUID loadSnapshotId,
            UUID assessmentId,
            String assessmentStatus,
            String revisionStatus,
            Instant now);

    void acknowledgeWarning(
            UUID revisionId,
            UUID assessmentId,
            UUID factorId,
            UUID actorId,
            String actorCapability,
            String rationale,
            Instant now);

    Set<UUID> acknowledgedFactors(UUID revisionId, UUID assessmentId);

    ActivationOutcome activate(
            UUID revisionId,
            String expectedChecksum,
            String idempotencyKey,
            UUID actorId,
            Instant now);

    record WorkflowState(
            UUID revisionId,
            UUID planId,
            UUID participantId,
            UUID ownerId,
            String mode,
            String revisionStatus,
            long revisionVersion,
            String validationChecksum,
            UUID loadSnapshotId,
            UUID assessmentId,
            UUID currentRevisionId) {
    }

    record ActivationOutcome(
            UUID revisionId,
            UUID planId,
            UUID supersededRevisionId,
            boolean repeated,
            Instant activatedAt) {
    }

    final class RevisionConflictException extends RuntimeException {
    }

    final class ImmutableRevisionException extends RuntimeException {
    }
}
