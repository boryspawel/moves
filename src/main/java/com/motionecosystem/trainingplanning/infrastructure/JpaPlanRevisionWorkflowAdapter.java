package com.motionecosystem.trainingplanning.infrastructure;

import com.motionecosystem.audit.api.TransactionalOutbox;
import com.motionecosystem.trainingplanning.PlannedSession;
import com.motionecosystem.trainingplanning.api.PlanRevisionWorkflowPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPlanRevisionWorkflowAdapter implements PlanRevisionWorkflowPersistence {

    private final EntityManager entityManager;
    private final TransactionalOutbox transactionalOutbox;

    @Override
    public WorkflowState state(UUID revisionId) {
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, revisionId);
        if (revision == null) {
            throw new IllegalArgumentException("revision not found");
        }
        TrainingPlanJpaEntity plan = entityManager.find(TrainingPlanJpaEntity.class, revision.planId);
        return state(revision, plan);
    }

    @Override
    public WorkflowState completeValidation(
            UUID revisionId,
            long expectedVersion,
            String checksum,
            UUID loadSnapshotId,
            UUID assessmentId,
            String assessmentStatus,
            String revisionStatus,
            Instant now) {
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, revisionId);
        if (revision != null) {
            entityManager.refresh(revision, LockModeType.PESSIMISTIC_WRITE);
        }
        if (revision == null || revision.version != expectedVersion) {
            throw new PlanRevisionWorkflowPersistence.RevisionConflictException();
        }
        if (!Set.of("DRAFT", "READY", "NEEDS_REVIEW", "BLOCKED").contains(revision.status)) {
            throw new PlanRevisionWorkflowPersistence.ImmutableRevisionException();
        }
        revision.validationChecksum = checksum;
        revision.loadSnapshotId = loadSnapshotId;
        revision.safetyAssessmentId = assessmentId;
        revision.assessmentStatus = assessmentStatus;
        revision.status = revisionStatus;
        revision.workflowValidatedAt = now;
        outbox("PlanRevision", revisionId, "PlanRevisionValidated",
                payload(revisionId, assessmentId, revisionStatus), now);
        if ("BLOCKED".equals(revisionStatus)) {
            outbox("PlanRevision", revisionId, "PlanRevisionBlocked",
                    payload(revisionId, assessmentId, revisionStatus), now);
        }
        entityManager.flush();
        return state(revision, entityManager.find(TrainingPlanJpaEntity.class, revision.planId));
    }

    @Override
    public void acknowledgeWarning(
            UUID revisionId,
            UUID assessmentId,
            UUID factorId,
            UUID actorId,
            String actorCapability,
            String rationale,
            Instant now) {
        Long existing = entityManager.createQuery("""
                SELECT COUNT(item.id) FROM PlanWarningAcknowledgementJpaEntity item
                WHERE item.revisionId = :revisionId AND item.assessmentId = :assessmentId
                  AND item.factorId = :factorId AND item.actorId = :actorId
                """, Long.class)
                .setParameter("revisionId", revisionId)
                .setParameter("assessmentId", assessmentId)
                .setParameter("factorId", factorId)
                .setParameter("actorId", actorId)
                .getSingleResult();
        if (existing > 0) {
            return;
        }
        PlanWarningAcknowledgementJpaEntity item = new PlanWarningAcknowledgementJpaEntity();
        item.id = UUID.randomUUID();
        item.revisionId = revisionId;
        item.assessmentId = assessmentId;
        item.factorId = factorId;
        item.actorId = actorId;
        item.actorCapability = actorCapability;
        item.rationale = rationale;
        item.acknowledgedAt = now;
        entityManager.persist(item);
    }

    @Override
    public Set<UUID> acknowledgedFactors(UUID revisionId, UUID assessmentId) {
        return Set.copyOf(entityManager.createQuery("""
                SELECT item.factorId FROM PlanWarningAcknowledgementJpaEntity item
                WHERE item.revisionId = :revisionId AND item.assessmentId = :assessmentId
                """, UUID.class)
                .setParameter("revisionId", revisionId)
                .setParameter("assessmentId", assessmentId)
                .getResultList());
    }

    @Override
    public ActivationOutcome activate(
            UUID revisionId,
            String expectedChecksum,
            String idempotencyKey,
            UUID actorId,
            Instant now) {
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, revisionId);
        if (revision == null) {
            throw new IllegalArgumentException("revision not found");
        }
        entityManager.refresh(revision, LockModeType.PESSIMISTIC_WRITE);
        TrainingPlanJpaEntity plan = entityManager.find(TrainingPlanJpaEntity.class, revision.planId);
        entityManager.refresh(plan, LockModeType.PESSIMISTIC_WRITE);
        List<PlanActivationRequestJpaEntity> prior = entityManager.createQuery("""
                SELECT item FROM PlanActivationRequestJpaEntity item
                WHERE item.revisionId = :revisionId AND item.idempotencyKey = :key
                """, PlanActivationRequestJpaEntity.class)
                .setParameter("revisionId", revisionId)
                .setParameter("key", idempotencyKey)
                .getResultList();
        if (!prior.isEmpty()) {
            return new ActivationOutcome(revisionId, revision.planId, null, true,
                    prior.getFirst().activatedAt);
        }
        if (!expectedChecksum.equals(revision.validationChecksum)) {
            throw new PlanRevisionWorkflowPersistence.RevisionConflictException();
        }
        if ("ACTIVE".equals(revision.status)) {
            return new ActivationOutcome(revisionId, revision.planId, null, true, now);
        }
        if (!Set.of("READY", "NEEDS_REVIEW", "BLOCKED").contains(revision.status)) {
            throw new PlanRevisionWorkflowPersistence.ImmutableRevisionException();
        }
        UUID previousId = plan.currentRevisionId;
        UUID superseded = null;
        if (previousId != null && !previousId.equals(revisionId)) {
            PlanRevisionJpaEntity previous = entityManager.find(
                    PlanRevisionJpaEntity.class, previousId, LockModeType.PESSIMISTIC_WRITE);
            if (previous != null && "ACTIVE".equals(previous.status)) {
                previous.status = "SUPERSEDED";
                superseded = previous.id;
                outbox("PlanRevision", previous.id, "PlanRevisionSuperseded",
                        "{\"revisionId\":\"" + previous.id + "\"}", now);
            }
        }
        revision.status = "ACTIVE";
        plan.status = "ACTIVE";
        plan.currentRevisionId = revisionId;
        List<PlannedSessionJpaEntity> sessions = sessions(revisionId);
        sessions.forEach(session -> {
            session.status = PlannedSession.SessionStatus.ASSIGNED;
            session.assignedAt = now;
            outbox("PlannedSession", session.id, "ExerciseSetAssigned",
                    "{\"revisionId\":\"" + revisionId + "\",\"sessionId\":\""
                            + session.id + "\"}", now);
        });
        PlanActivationRequestJpaEntity request = new PlanActivationRequestJpaEntity();
        request.id = UUID.randomUUID();
        request.revisionId = revisionId;
        request.idempotencyKey = idempotencyKey;
        request.actorId = actorId;
        request.activatedAt = now;
        entityManager.persist(request);
        outbox("PlanRevision", revisionId, "PlanRevisionActivated",
                "{\"revisionId\":\"" + revisionId + "\",\"planId\":\""
                        + revision.planId + "\"}", now);
        entityManager.flush();
        return new ActivationOutcome(revisionId, revision.planId, superseded, false, now);
    }

    private List<PlannedSessionJpaEntity> sessions(UUID revisionId) {
        return entityManager.createQuery("""
                SELECT session FROM PlannedSessionJpaEntity session,
                    MicrocycleJpaEntity microcycle, TrainingCycleJpaEntity cycle
                WHERE session.microcycleId = microcycle.id AND microcycle.cycleId = cycle.id
                  AND cycle.revisionId = :revisionId
                """, PlannedSessionJpaEntity.class)
                .setParameter("revisionId", revisionId)
                .getResultList();
    }

    private void outbox(
            String aggregateType, UUID aggregateId, String eventType, String payload, Instant now) {
        transactionalOutbox.append(aggregateType, aggregateId, eventType, payload, now);
    }

    private static WorkflowState state(PlanRevisionJpaEntity revision, TrainingPlanJpaEntity plan) {
        return new WorkflowState(
                revision.id,
                revision.planId,
                plan.participantAccountId,
                plan.ownerAccountId,
                plan.mode,
                revision.status,
                revision.version,
                revision.validationChecksum,
                revision.loadSnapshotId,
                revision.safetyAssessmentId,
                plan.currentRevisionId);
    }

    private static String payload(UUID revisionId, UUID assessmentId, String status) {
        return "{\"revisionId\":\"" + revisionId + "\",\"assessmentId\":\""
                + assessmentId + "\",\"status\":\"" + status + "\"}";
    }
}
