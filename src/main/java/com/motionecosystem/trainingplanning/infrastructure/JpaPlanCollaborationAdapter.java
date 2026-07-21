package com.motionecosystem.trainingplanning.infrastructure;

import com.motionecosystem.trainingplanning.PlanCollaborationPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPlanCollaborationAdapter implements PlanCollaborationPersistence {
    private final EntityManager entityManager;

    @Override
    public void saveCollaborator(CollaboratorData collaborator) {
        entityManager.persist(new PlanCollaboratorJpaEntity(collaborator));
        entityManager.flush();
    }

    @Override
    public Optional<CollaboratorData> findActiveCollaborator(UUID planId, UUID specialistId) {
        return entityManager.createQuery("""
                SELECT collaborator FROM PlanCollaboratorJpaEntity collaborator
                WHERE collaborator.planId=:planId AND collaborator.specialistId=:specialistId
                  AND collaborator.status='ACTIVE'
                """, PlanCollaboratorJpaEntity.class)
                .setParameter("planId", planId).setParameter("specialistId", specialistId)
                .getResultStream().findFirst().map(PlanCollaboratorJpaEntity::data);
    }

    @Override
    public CollaboratorData endCollaborator(UUID collaboratorId, UUID planId, Instant endedAt) {
        PlanCollaboratorJpaEntity item = entityManager.find(
                PlanCollaboratorJpaEntity.class, collaboratorId, LockModeType.PESSIMISTIC_WRITE);
        if (item == null || !item.planId.equals(planId)) {
            throw new IllegalArgumentException("plan collaborator not found");
        }
        if (!"ACTIVE".equals(item.status)) {
            throw new IllegalStateException("plan collaboration is already ended");
        }
        item.status = "ENDED";
        item.endedAt = endedAt;
        return item.data();
    }

    @Override
    public void saveReview(ReviewData review) {
        entityManager.persist(new PlanReviewRequestJpaEntity(review));
        entityManager.flush();
    }

    @Override
    public Optional<ReviewData> findReview(UUID reviewId) {
        return Optional.ofNullable(entityManager.find(PlanReviewRequestJpaEntity.class, reviewId))
                .map(PlanReviewRequestJpaEntity::data);
    }

    @Override
    public ReviewData decideReview(UUID reviewId, UUID reviewerId, String status,
                                   String decisionReference, Instant decidedAt) {
        PlanReviewRequestJpaEntity item = entityManager.find(
                PlanReviewRequestJpaEntity.class, reviewId, LockModeType.PESSIMISTIC_WRITE);
        if (item == null || !item.reviewerId.equals(reviewerId)) {
            throw new IllegalArgumentException("review request not found");
        }
        if (!"OPEN".equals(item.status)) throw new IllegalStateException("review request is already decided");
        item.status = status; item.decisionReference = decisionReference; item.decidedAt = decidedAt;
        return item.data();
    }
}
