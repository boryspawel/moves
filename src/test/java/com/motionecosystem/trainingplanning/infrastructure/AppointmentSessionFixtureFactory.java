package com.motionecosystem.trainingplanning.infrastructure;

import com.motionecosystem.trainingplanning.PlannedSession;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** Test-only JPA fixture support that preserves native V2 session kind and creation source. */
public final class AppointmentSessionFixtureFactory {
    private AppointmentSessionFixtureFactory() { }

    public static void setStatus(EntityManager entityManager, UUID sessionId,
                                 PlannedSession.SessionStatus status) {
        PlannedSessionJpaEntity session = entityManager.find(PlannedSessionJpaEntity.class, sessionId);
        if (session == null) throw new IllegalArgumentException("planned session fixture is missing");
        session.status = status;
        entityManager.flush();
    }

    public static void setPlanAndRevisionStatus(EntityManager entityManager, UUID sessionId,
                                                String planStatus, String revisionStatus) {
        PlannedSessionJpaEntity session = entityManager.find(PlannedSessionJpaEntity.class, sessionId);
        if (session == null) throw new IllegalArgumentException("planned session fixture is missing");
        MicrocycleJpaEntity microcycle = entityManager.find(MicrocycleJpaEntity.class, session.microcycleId);
        TrainingCycleJpaEntity cycle = entityManager.find(TrainingCycleJpaEntity.class, microcycle.cycleId);
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, cycle.revisionId);
        TrainingPlanJpaEntity plan = entityManager.find(TrainingPlanJpaEntity.class, revision.planId);
        plan.status = planStatus;
        revision.status = revisionStatus;
        entityManager.flush();
    }

    public static void setCurrentRevision(EntityManager entityManager, UUID sessionId, UUID currentRevisionId) {
        PlannedSessionJpaEntity session = entityManager.find(PlannedSessionJpaEntity.class, sessionId);
        if (session == null) throw new IllegalArgumentException("planned session fixture is missing");
        MicrocycleJpaEntity microcycle = entityManager.find(MicrocycleJpaEntity.class, session.microcycleId);
        TrainingCycleJpaEntity cycle = entityManager.find(TrainingCycleJpaEntity.class, microcycle.cycleId);
        PlanRevisionJpaEntity revision = entityManager.find(PlanRevisionJpaEntity.class, cycle.revisionId);
        TrainingPlanJpaEntity plan = entityManager.find(TrainingPlanJpaEntity.class, revision.planId);
        plan.currentRevisionId = currentRevisionId;
        entityManager.flush();
    }
}
