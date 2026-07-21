package com.motionecosystem.trainingplanning;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlanCollaborationPersistence {

    void saveCollaborator(CollaboratorData collaborator);

    Optional<CollaboratorData> findActiveCollaborator(UUID planId, UUID specialistId);

    CollaboratorData endCollaborator(UUID collaboratorId, UUID planId, Instant endedAt);

    void saveReview(ReviewData review);

    Optional<ReviewData> findReview(UUID reviewId);

    ReviewData decideReview(UUID reviewId, UUID reviewerId, String status,
                            String decisionReference, Instant decidedAt);

    record CollaboratorData(UUID id, UUID planId, UUID specialistId, String professionalRole,
                            Set<String> scopes, String status, UUID addedBy, Instant addedAt) {
        public CollaboratorData {
            scopes = Set.copyOf(scopes);
        }
    }

    record ReviewData(UUID id, UUID revisionId, UUID requestedBy, UUID reviewerId,
                      String status, String requestReference, String decisionReference,
                      Instant requestedAt, Instant decidedAt) {
    }
}
