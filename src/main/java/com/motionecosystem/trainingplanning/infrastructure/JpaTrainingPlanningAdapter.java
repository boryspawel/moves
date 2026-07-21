package com.motionecosystem.trainingplanning.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.motionecosystem.trainingplanning.ExercisePrescription;
import com.motionecosystem.trainingplanning.Microcycle;
import com.motionecosystem.trainingplanning.PlannedSession;
import com.motionecosystem.trainingplanning.TrainingCycle;
import com.motionecosystem.trainingplanning.TrainingGoal;
import com.motionecosystem.trainingplanning.TrainingPlan;
import com.motionecosystem.trainingplanning.TrainingPlanningPersistence;
import com.motionecosystem.trainingplanning.api.PlannedSessionExecutionPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaTrainingPlanningAdapter implements TrainingPlanningPersistence, PlannedSessionExecutionPort {

    private final EntityManager entityManager;

    @Override
    public void save(TrainingGoal goal, TrainingPlan plan, TrainingCycle cycle, Microcycle microcycle,
                     PlannedSession session, List<ExercisePrescription> prescriptions) {
        entityManager.persist(new TrainingGoalJpaEntity(goal));
        entityManager.persist(new TrainingPlanJpaEntity(plan));
        entityManager.persist(new TrainingCycleJpaEntity(cycle));
        entityManager.persist(new MicrocycleJpaEntity(microcycle));
        entityManager.persist(new PlannedSessionJpaEntity(session));
        prescriptions.stream().map(ExercisePrescriptionJpaEntity::new).forEach(entityManager::persist);
    }

    @Override
    public List<StoredSession> findParticipantSessions(UUID participantAccountId) {
        List<PlannedSessionJpaEntity> sessions = entityManager.createQuery("""
                SELECT session FROM PlannedSessionJpaEntity session
                WHERE session.participantAccountId = :participantAccountId
                  AND session.status <> :draftStatus
                ORDER BY session.assignedAt, session.id
                """, PlannedSessionJpaEntity.class)
                .setParameter("participantAccountId", participantAccountId)
                .setParameter("draftStatus", PlannedSession.SessionStatus.DRAFT)
                .getResultList();
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ExercisePrescriptionJpaEntity>> prescriptions = prescriptions(
                sessions.stream().map(PlannedSessionJpaEntity::id).toList());
        return sessions.stream().map(session -> new StoredSession(
                session.id(), session.title(), session.kind(), session.status(), session.assignedAt(),
                prescriptions.getOrDefault(session.id(), List.of()).stream()
                        .map(JpaTrainingPlanningAdapter::storedPrescription)
                        .toList())).toList();
    }

    @Override
    public Optional<PlannedSessionSnapshot> lockOwnedSession(UUID sessionId, UUID participantAccountId) {
        PlannedSessionJpaEntity session = entityManager.find(
                PlannedSessionJpaEntity.class, sessionId, LockModeType.PESSIMISTIC_WRITE);
        if (session == null || !session.participantAccountId().equals(participantAccountId)) {
            return Optional.empty();
        }
        return Optional.of(snapshot(session));
    }

    @Override
    public Optional<PlannedSessionSnapshot> findSession(UUID sessionId) {
        return Optional.ofNullable(entityManager.find(PlannedSessionJpaEntity.class, sessionId))
                .map(this::snapshot);
    }

    @Override
    public void markCompleted(UUID sessionId) {
        PlannedSessionJpaEntity session = entityManager.find(PlannedSessionJpaEntity.class, sessionId);
        if (session == null) {
            throw new IllegalStateException("planned session disappeared during execution");
        }
        session.complete();
    }

    private PlannedSessionSnapshot snapshot(PlannedSessionJpaEntity session) {
        List<PrescriptionSnapshot> items = prescriptions(List.of(session.id()))
                .getOrDefault(session.id(), List.of()).stream()
                .map(item -> new PrescriptionSnapshot(item.id(), item.exerciseVersionId(), item.position()))
                .toList();
        return new PlannedSessionSnapshot(session.id(), session.participantAccountId(),
                SessionState.valueOf(session.status().name()), items);
    }

    private Map<UUID, List<ExercisePrescriptionJpaEntity>> prescriptions(List<UUID> sessionIds) {
        return entityManager.createQuery("""
                SELECT prescription FROM ExercisePrescriptionJpaEntity prescription
                WHERE prescription.plannedSessionId IN :sessionIds
                ORDER BY prescription.plannedSessionId, prescription.position
                """, ExercisePrescriptionJpaEntity.class)
                .setParameter("sessionIds", sessionIds)
                .getResultList().stream()
                .collect(Collectors.groupingBy(ExercisePrescriptionJpaEntity::plannedSessionId));
    }

    private static StoredPrescription storedPrescription(ExercisePrescriptionJpaEntity item) {
        return new StoredPrescription(item.id(), item.exerciseVersionId(), item.position(), item.targetSets(),
                item.targetRepetitions(), item.targetDurationSeconds(), item.targetLoadKg(), item.notes());
    }
}
