package com.motionecosystem.trainingplanning.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.motionecosystem.trainingplanning.TrainingPlanningModel;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Persistence;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaTrainingPlanningV2Adapter implements TrainingPlanningV2Persistence, PlanRevisionQueryPort {

    private final EntityManager entityManager;

    @Override
    public void createDraft(TrainingPlanningModel.PlanDraft plan, TrainingPlanningModel.Revision revision) {
        TrainingPlanJpaEntity planEntity = new TrainingPlanJpaEntity(plan);
        planEntity.currentRevisionId = null;
        entityManager.persist(planEntity);
        entityManager.persist(new PlanRevisionJpaEntity(revision));
        entityManager.flush();
        planEntity.currentRevision(revision.id());
        entityManager.flush();
    }

    @Override
    public void addGoal(UUID revisionId, long expectedVersion, TrainingPlanningModel.Goal goal,
                        List<TrainingPlanningModel.GoalOutcome> outcomes, Instant updatedAt) {
        touch(revisionId, expectedVersion, updatedAt);
        entityManager.persist(new TrainingGoalJpaEntity(goal));
        outcomes.stream().map(GoalOutcomeJpaEntity::new).forEach(entityManager::persist);
        entityManager.flush();
    }

    @Override
    public void addCycle(UUID revisionId, long expectedVersion,
                         TrainingPlanningModel.Cycle cycle, Instant updatedAt) {
        touch(revisionId, expectedVersion, updatedAt);
        entityManager.persist(new TrainingCycleJpaEntity(cycle));
        entityManager.flush();
    }

    @Override
    public void addMicrocycle(UUID revisionId, long expectedVersion,
                              TrainingPlanningModel.MicrocycleV2 microcycle, Instant updatedAt) {
        touch(revisionId, expectedVersion, updatedAt);
        TrainingCycleJpaEntity cycle = entityManager.find(TrainingCycleJpaEntity.class, microcycle.cycleId());
        if (cycle == null || !revisionId.equals(cycle.revisionId)) {
            throw new IllegalArgumentException("cycle does not belong to revision");
        }
        entityManager.persist(new MicrocycleJpaEntity(microcycle));
        entityManager.flush();
    }

    @Override
    public void addSession(UUID revisionId, long expectedVersion,
                           TrainingPlanningModel.Session session, Instant updatedAt) {
        touch(revisionId, expectedVersion, updatedAt);
        if (!microcycleBelongsToRevision(session.microcycleId(), revisionId)) {
            throw new IllegalArgumentException("microcycle does not belong to revision");
        }
        entityManager.persist(new PlannedSessionJpaEntity(session));
        entityManager.flush();
    }

    @Override
    public void addPrescription(UUID revisionId, long expectedVersion,
                                TrainingPlanningModel.Prescription prescription, Instant updatedAt) {
        touch(revisionId, expectedVersion, updatedAt);
        if (!sessionBelongsToRevision(prescription.plannedSessionId(), revisionId)) {
            throw new IllegalArgumentException("session does not belong to revision");
        }
        entityManager.persist(new ExercisePrescriptionJpaEntity(prescription));
        entityManager.flush();
    }

    @Override
    public void reorderPrescriptions(UUID revisionId, long expectedVersion, UUID sessionId,
                                     List<UUID> orderedPrescriptionIds, Instant updatedAt) {
        touch(revisionId, expectedVersion, updatedAt);
        if (!sessionBelongsToRevision(sessionId, revisionId)) {
            throw new IllegalArgumentException("session does not belong to revision");
        }
        List<ExercisePrescriptionJpaEntity> items = prescriptionsForSessions(Set.of(sessionId));
        Set<UUID> existing = items.stream().map(item -> item.id).collect(Collectors.toSet());
        if (orderedPrescriptionIds.size() != existing.size()
                || !existing.equals(Set.copyOf(orderedPrescriptionIds))) {
            throw new IllegalArgumentException("reorder must contain every prescription exactly once");
        }
        Map<UUID, ExercisePrescriptionJpaEntity> byId = items.stream()
                .collect(Collectors.toMap(item -> item.id, Function.identity()));
        for (int index = 0; index < orderedPrescriptionIds.size(); index++) {
            byId.get(orderedPrescriptionIds.get(index)).position(1_000_000 + index);
        }
        entityManager.flush();
        for (int index = 0; index < orderedPrescriptionIds.size(); index++) {
            byId.get(orderedPrescriptionIds.get(index)).position(index + 1);
        }
        entityManager.flush();
    }

    @Override
    public void addLoadBudget(UUID revisionId, long expectedVersion,
                              TrainingPlanningModel.LoadBudget budget, Instant updatedAt) {
        touch(revisionId, expectedVersion, updatedAt);
        entityManager.persist(new PlanLoadBudgetJpaEntity(budget));
        entityManager.flush();
    }

    @Override
    public UUID cloneRevision(UUID planId, UUID basedOnRevisionId, UUID authorAccountId,
                              String authorCapability, Instant now) {
        TrainingPlanJpaEntity plan = entityManager.find(
                TrainingPlanJpaEntity.class, planId, LockModeType.PESSIMISTIC_WRITE);
        PlanRevisionJpaEntity source = entityManager.find(PlanRevisionJpaEntity.class, basedOnRevisionId);
        if (plan == null || source == null || !planId.equals(source.planId)) {
            throw new IllegalArgumentException("base revision does not belong to plan");
        }
        int number = entityManager.createQuery("""
                SELECT MAX(revision.revisionNumber) FROM PlanRevisionJpaEntity revision
                WHERE revision.planId = :planId
                """, Integer.class).setParameter("planId", planId).getSingleResult() + 1;
        UUID revisionId = UUID.randomUUID();
        TrainingPlanningModel.Revision revision = new TrainingPlanningModel.Revision(
                revisionId, planId, number, basedOnRevisionId, TrainingPlanningModel.RevisionStatus.DRAFT,
                source.phaseIntent, source.validFrom, source.validTo, authorAccountId, authorCapability,
                "NATIVE_V2", "NOT_ASSESSED", now, now, 0);
        entityManager.persist(new PlanRevisionJpaEntity(revision));
        cloneTree(source.id, revisionId, planId, plan.participantAccountId, authorAccountId, now);
        if ("DRAFT".equals(plan.status)) {
            plan.currentRevision(revisionId);
        }
        entityManager.flush();
        return revisionId;
    }

    @Override
    public void saveStructuralValidation(TrainingPlanningModel.StructuralValidation validation) {
        PlanRevisionJpaEntity revision = entityManager.find(
                PlanRevisionJpaEntity.class, validation.revisionId(), LockModeType.PESSIMISTIC_WRITE);
        if (revision == null || revision.version != validation.draftVersion()) {
            throw new RevisionConflictException();
        }
        Long existing = entityManager.createQuery("""
                SELECT COUNT(item.id) FROM PlanStructuralValidationJpaEntity item
                WHERE item.revisionId = :revisionId AND item.draftVersion = :draftVersion
                """, Long.class).setParameter("revisionId", validation.revisionId())
                .setParameter("draftVersion", validation.draftVersion()).getSingleResult();
        if (existing > 0) {
            return;
        }
        entityManager.persist(new PlanStructuralValidationJpaEntity(validation));
        for (int index = 0; index < validation.violations().size(); index++) {
            entityManager.persist(new PlanStructuralViolationJpaEntity(
                    validation.id(), index + 1, validation.violations().get(index)));
        }
        entityManager.flush();
    }

    @Override
    public Optional<PlanAccess> findPlanAccess(UUID planId) {
        TrainingPlanJpaEntity plan = entityManager.find(TrainingPlanJpaEntity.class, planId);
        return Optional.ofNullable(plan).map(item -> new PlanAccess(item.id, item.participantAccountId,
                item.name, item.purpose, item.ownerAccountId, item.mode, item.status, item.currentRevisionId));
    }

    @Override
    public Optional<RevisionAccess> findRevisionAccess(UUID revisionId) {
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, revisionId);
        if (revision == null) {
            return Optional.empty();
        }
        TrainingPlanJpaEntity plan = entityManager.find(TrainingPlanJpaEntity.class, revision.planId);
        return Optional.of(new RevisionAccess(revision.id, revision.planId, plan.participantAccountId,
                plan.ownerAccountId, plan.mode, revision.status, revision.revisionNumber, revision.version));
    }

    @Override
    public List<RevisionHistoryItem> revisionHistory(UUID planId) {
        return entityManager.createQuery("""
                SELECT revision FROM PlanRevisionJpaEntity revision
                WHERE revision.planId = :planId ORDER BY revision.revisionNumber
                """, PlanRevisionJpaEntity.class).setParameter("planId", planId).getResultList().stream()
                .map(item -> new RevisionHistoryItem(item.id, item.revisionNumber, item.basedOnRevisionId,
                        item.status, item.migrationOrigin, item.assessmentStatus, item.version, item.createdAt))
                .toList();
    }

    @Override
    public Optional<PlanRevisionSnapshot> findRevision(UUID revisionId) {
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, revisionId);
        if (revision == null) {
            return Optional.empty();
        }
        TrainingPlanJpaEntity plan = entityManager.find(TrainingPlanJpaEntity.class, revision.planId);
        List<TrainingGoalJpaEntity> goals = entityManager.createQuery("""
                SELECT goal FROM TrainingGoalJpaEntity goal
                WHERE goal.revisionId = :revisionId ORDER BY goal.priority, goal.id
                """, TrainingGoalJpaEntity.class).setParameter("revisionId", revisionId).getResultList();
        Map<UUID, List<GoalOutcomeJpaEntity>> outcomes = outcomes(
                goals.stream().map(item -> item.id).collect(Collectors.toSet()));
        List<TrainingCycleJpaEntity> cycles = cycles(revisionId);
        Map<UUID, List<MicrocycleJpaEntity>> microcycles = microcycles(
                cycles.stream().map(item -> item.id).collect(Collectors.toSet()));
        Set<UUID> microcycleIds = microcycles.values().stream().flatMap(List::stream)
                .map(item -> item.id).collect(Collectors.toSet());
        Map<UUID, List<PlannedSessionJpaEntity>> sessions = sessions(microcycleIds);
        Set<UUID> sessionIds = sessions.values().stream().flatMap(List::stream)
                .map(item -> item.id).collect(Collectors.toSet());
        Map<UUID, List<ExercisePrescriptionJpaEntity>> prescriptions = prescriptionsForSessions(sessionIds)
                .stream().collect(Collectors.groupingBy(item -> item.plannedSessionId));
        List<PlanLoadBudgetJpaEntity> budgets = entityManager.createQuery("""
                SELECT budget FROM PlanLoadBudgetJpaEntity budget
                WHERE budget.revisionId = :revisionId ORDER BY budget.channel, budget.unit
                """, PlanLoadBudgetJpaEntity.class).setParameter("revisionId", revisionId).getResultList();
        return Optional.of(new PlanRevisionSnapshot(revision.id, revision.planId, plan.participantAccountId,
                revision.revisionNumber, revision.basedOnRevisionId, revision.version, revision.status,
                revision.authorAccountId, revision.authorCapability, revision.createdAt,
                revision.migrationOrigin, revision.assessmentStatus, revision.phaseIntent,
                revision.validFrom, revision.validTo,
                goals.stream().map(goal -> goalSnapshot(goal, outcomes.getOrDefault(goal.id, List.of()))).toList(),
                cycles.stream().map(cycle -> cycleSnapshot(cycle,
                        microcycles.getOrDefault(cycle.id, List.of()), sessions, prescriptions)).toList(),
                budgets.stream().map(item -> new LoadBudgetSnapshot(item.id, item.channel, item.low,
                        item.high, item.unit, item.action)).toList()));
    }

    private void touch(UUID revisionId, long expectedVersion, Instant updatedAt) {
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, revisionId);
        if (revision == null) {
            throw new IllegalArgumentException("revision not found");
        }
        revision.requireDraft(expectedVersion, updatedAt);
    }

    private boolean microcycleBelongsToRevision(UUID microcycleId, UUID revisionId) {
        Long count = entityManager.createQuery("""
                SELECT COUNT(microcycle.id) FROM MicrocycleJpaEntity microcycle, TrainingCycleJpaEntity cycle
                WHERE microcycle.id = :microcycleId AND microcycle.cycleId = cycle.id
                  AND cycle.revisionId = :revisionId
                """, Long.class).setParameter("microcycleId", microcycleId)
                .setParameter("revisionId", revisionId).getSingleResult();
        return count == 1;
    }

    private boolean sessionBelongsToRevision(UUID sessionId, UUID revisionId) {
        Long count = entityManager.createQuery("""
                SELECT COUNT(session.id)
                FROM PlannedSessionJpaEntity session, MicrocycleJpaEntity microcycle,
                     TrainingCycleJpaEntity cycle
                WHERE session.id = :sessionId AND session.microcycleId = microcycle.id
                  AND microcycle.cycleId = cycle.id AND cycle.revisionId = :revisionId
                """, Long.class).setParameter("sessionId", sessionId)
                .setParameter("revisionId", revisionId).getSingleResult();
        return count == 1;
    }

    private void cloneTree(UUID sourceRevisionId, UUID targetRevisionId, UUID planId,
                           UUID participantId, UUID actorId, Instant now) {
        Map<UUID, UUID> goalIds = new HashMap<>();
        List<TrainingGoalJpaEntity> sourceGoals = entityManager.createQuery("""
                SELECT goal FROM TrainingGoalJpaEntity goal WHERE goal.revisionId = :revisionId
                """, TrainingGoalJpaEntity.class).setParameter("revisionId", sourceRevisionId).getResultList();
        for (TrainingGoalJpaEntity source : sourceGoals) {
            UUID newId = UUID.randomUUID();
            goalIds.put(source.id, newId);
            TrainingPlanningModel.Goal goal = new TrainingPlanningModel.Goal(newId, targetRevisionId,
                    participantId, TrainingPlanningModel.GoalPerspective.valueOf(source.perspective),
                    source.category, source.title, source.description, source.priority,
                    TrainingPlanningModel.GoalStatus.valueOf(source.status), source.targetDate, actorId, now);
            entityManager.persist(new TrainingGoalJpaEntity(goal));
        }
        for (GoalOutcomeJpaEntity source : outcomes(goalIds.keySet()).values().stream()
                .flatMap(List::stream).toList()) {
            entityManager.persist(new GoalOutcomeJpaEntity(new TrainingPlanningModel.GoalOutcome(
                    UUID.randomUUID(), goalIds.get(source.goalId), source.metricCode, source.baseline,
                    source.target, source.unit, source.measurementMethod, source.evidenceSource)));
        }

        Map<UUID, UUID> cycleIds = new HashMap<>();
        Map<UUID, UUID> microcycleIds = new HashMap<>();
        Map<UUID, UUID> sessionIds = new HashMap<>();
        List<TrainingCycleJpaEntity> sourceCycles = cycles(sourceRevisionId);
        Map<UUID, List<MicrocycleJpaEntity>> sourceMicros = microcycles(
                sourceCycles.stream().map(item -> item.id).collect(Collectors.toSet()));
        Set<UUID> sourceMicroIds = sourceMicros.values().stream().flatMap(List::stream)
                .map(item -> item.id).collect(Collectors.toSet());
        Map<UUID, List<PlannedSessionJpaEntity>> sourceSessions = sessions(sourceMicroIds);
        Set<UUID> sourceSessionIds = sourceSessions.values().stream().flatMap(List::stream)
                .map(item -> item.id).collect(Collectors.toSet());
        List<ExercisePrescriptionJpaEntity> sourcePrescriptions = prescriptionsForSessions(sourceSessionIds);
        for (TrainingCycleJpaEntity sourceCycle : sourceCycles) {
            UUID cycleId = UUID.randomUUID();
            cycleIds.put(sourceCycle.id, cycleId);
            entityManager.persist(new TrainingCycleJpaEntity(new TrainingPlanningModel.Cycle(cycleId, planId,
                    targetRevisionId, sourceCycle.sequenceNumber, sourceCycle.name, sourceCycle.startDate,
                    sourceCycle.endDate, sourceCycle.phaseIntent, sourceCycle.phaseGoal)));
            for (MicrocycleJpaEntity sourceMicro : sourceMicros.getOrDefault(sourceCycle.id, List.of())) {
                UUID microId = UUID.randomUUID();
                microcycleIds.put(sourceMicro.id, microId);
                entityManager.persist(new MicrocycleJpaEntity(new TrainingPlanningModel.MicrocycleV2(microId,
                        cycleId, sourceMicro.sequenceNumber, sourceMicro.name, sourceMicro.startDate,
                        sourceMicro.endDate, sourceMicro.phaseIntent, sourceMicro.phaseGoal)));
                for (PlannedSessionJpaEntity sourceSession : sourceSessions
                        .getOrDefault(sourceMicro.id, List.of())) {
                    UUID sessionId = UUID.randomUUID();
                    sessionIds.put(sourceSession.id, sessionId);
                    entityManager.persist(new PlannedSessionJpaEntity(new TrainingPlanningModel.Session(sessionId,
                            microId, participantId, sourceSession.title, sourceSession.scheduledDate,
                            sourceSession.availableFrom, sourceSession.availableTo,
                            sourceSession.expectedDurationMinutes == null ? 60 : sourceSession.expectedDurationMinutes,
                            now)));
                }
            }
        }
        for (ExercisePrescriptionJpaEntity source : sourcePrescriptions) {
            TrainingPlanningModel.Prescription copy = prescriptionCopy(
                    source, sessionIds.get(source.plannedSessionId));
            entityManager.persist(new ExercisePrescriptionJpaEntity(copy));
        }
        List<PlanLoadBudgetJpaEntity> budgets = entityManager.createQuery("""
                SELECT budget FROM PlanLoadBudgetJpaEntity budget WHERE budget.revisionId = :revisionId
                """, PlanLoadBudgetJpaEntity.class).setParameter("revisionId", sourceRevisionId).getResultList();
        budgets.forEach(source -> entityManager.persist(new PlanLoadBudgetJpaEntity(
                new TrainingPlanningModel.LoadBudget(UUID.randomUUID(), targetRevisionId, source.channel,
                        source.low, source.high, source.unit,
                        TrainingPlanningModel.BudgetAction.valueOf(source.action), actorId, now))));
    }

    private static TrainingPlanningModel.Prescription prescriptionCopy(
            ExercisePrescriptionJpaEntity source, UUID sessionId) {
        String dose = "LEGACY_UNTYPED".equals(source.doseType)
                ? inferLegacyDose(source) : source.doseType;
        return new TrainingPlanningModel.Prescription(UUID.randomUUID(), sessionId, source.exerciseVersionId,
                source.position, source.side == null ? TrainingPlanningModel.PrescriptionSide.NOT_APPLICABLE
                : TrainingPlanningModel.PrescriptionSide.valueOf(source.side),
                TrainingPlanningModel.DoseType.valueOf(dose), source.targetSets, source.targetRepetitions,
                source.targetDurationSeconds, source.distanceMeters, source.contacts, source.externalLoadValue,
                source.externalLoadUnit, source.intensityType == null ? null
                : TrainingPlanningModel.IntensityType.valueOf(source.intensityType), source.intensityValue,
                source.intensityZone, source.tempo, source.rangeOfMotion, source.restSeconds,
                source.substituteGroup, source.notes);
    }

    private static String inferLegacyDose(ExercisePrescriptionJpaEntity source) {
        if (source.targetDurationSeconds != null && source.targetRepetitions == null) {
            return "ISOMETRIC";
        }
        return "DYNAMIC_RESISTANCE";
    }

    private List<TrainingCycleJpaEntity> cycles(UUID revisionId) {
        return entityManager.createQuery("""
                SELECT cycle FROM TrainingCycleJpaEntity cycle
                WHERE cycle.revisionId = :revisionId ORDER BY cycle.sequenceNumber, cycle.id
                """, TrainingCycleJpaEntity.class).setParameter("revisionId", revisionId).getResultList();
    }

    private Map<UUID, List<GoalOutcomeJpaEntity>> outcomes(Set<UUID> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("""
                SELECT outcome FROM GoalOutcomeJpaEntity outcome WHERE outcome.goalId IN :ids
                ORDER BY outcome.metricCode, outcome.id
                """, GoalOutcomeJpaEntity.class).setParameter("ids", goalIds).getResultList().stream()
                .collect(Collectors.groupingBy(item -> item.goalId));
    }

    private Map<UUID, List<MicrocycleJpaEntity>> microcycles(Set<UUID> cycleIds) {
        if (cycleIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("""
                SELECT microcycle FROM MicrocycleJpaEntity microcycle WHERE microcycle.cycleId IN :ids
                ORDER BY microcycle.cycleId, microcycle.sequenceNumber, microcycle.id
                """, MicrocycleJpaEntity.class).setParameter("ids", cycleIds).getResultList().stream()
                .collect(Collectors.groupingBy(item -> item.cycleId));
    }

    private Map<UUID, List<PlannedSessionJpaEntity>> sessions(Set<UUID> microcycleIds) {
        if (microcycleIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("""
                SELECT session FROM PlannedSessionJpaEntity session WHERE session.microcycleId IN :ids
                ORDER BY session.microcycleId, session.scheduledDate, session.id
                """, PlannedSessionJpaEntity.class).setParameter("ids", microcycleIds).getResultList().stream()
                .collect(Collectors.groupingBy(item -> item.microcycleId));
    }

    private List<ExercisePrescriptionJpaEntity> prescriptionsForSessions(Set<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                SELECT prescription FROM ExercisePrescriptionJpaEntity prescription
                WHERE prescription.plannedSessionId IN :ids
                ORDER BY prescription.plannedSessionId, prescription.position, prescription.id
                """, ExercisePrescriptionJpaEntity.class).setParameter("ids", sessionIds).getResultList();
    }

    private static GoalSnapshot goalSnapshot(TrainingGoalJpaEntity goal,
                                             List<GoalOutcomeJpaEntity> outcomes) {
        return new GoalSnapshot(goal.id, goal.perspective, goal.category, goal.title, goal.priority,
                goal.status, goal.targetDate, outcomes.stream().map(item -> new GoalOutcomeSnapshot(item.id,
                        item.metricCode, item.baseline, item.target, item.unit, item.measurementMethod,
                        item.evidenceSource)).toList());
    }

    private static CycleSnapshot cycleSnapshot(TrainingCycleJpaEntity cycle,
                                                List<MicrocycleJpaEntity> microcycles,
                                                Map<UUID, List<PlannedSessionJpaEntity>> sessions,
                                                Map<UUID, List<ExercisePrescriptionJpaEntity>> prescriptions) {
        return new CycleSnapshot(cycle.id, cycle.sequenceNumber, cycle.name, cycle.startDate, cycle.endDate,
                cycle.phaseIntent, cycle.phaseGoal, microcycles.stream().map(micro -> new MicrocycleSnapshot(
                        micro.id, micro.sequenceNumber, micro.name, micro.startDate, micro.endDate,
                        micro.phaseIntent, micro.phaseGoal, sessions.getOrDefault(micro.id, List.of()).stream()
                        .map(session -> sessionSnapshot(session,
                                prescriptions.getOrDefault(session.id, List.of()))).toList())).toList());
    }

    private static SessionSnapshot sessionSnapshot(PlannedSessionJpaEntity session,
                                                   List<ExercisePrescriptionJpaEntity> prescriptions) {
        return new SessionSnapshot(session.id, session.title, session.scheduledDate, session.availableFrom,
                session.availableTo, session.expectedDurationMinutes == null ? 0 : session.expectedDurationMinutes,
                session.status.name(),
                prescriptions.stream().map(JpaTrainingPlanningV2Adapter::prescriptionSnapshot).toList());
    }

    private static PrescriptionSnapshot prescriptionSnapshot(ExercisePrescriptionJpaEntity item) {
        return new PrescriptionSnapshot(item.id, item.exerciseVersionId, item.position, item.side,
                item.doseType, item.targetSets, item.targetRepetitions, item.targetDurationSeconds,
                item.distanceMeters, item.contacts, item.externalLoadValue, item.externalLoadUnit,
                item.intensityType, item.intensityValue, item.intensityZone, item.tempo, item.rangeOfMotion,
                item.restSeconds, item.substituteGroup, item.notes);
    }
}
