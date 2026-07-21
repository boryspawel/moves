package com.motionecosystem.trainingplanning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.trainingplanning.PlannedSession.SessionKind;
import com.motionecosystem.trainingplanning.PlannedSession.SessionStatus;

/** Application persistence port. Its implementation and JPA entities stay inside this module. */
public interface TrainingPlanningPersistence {

    void save(TrainingGoal goal, TrainingPlan plan, TrainingCycle cycle, Microcycle microcycle,
              PlannedSession session, List<ExercisePrescription> prescriptions);

    List<StoredSession> findParticipantSessions(UUID participantAccountId);

    record StoredSession(UUID id, String title, SessionKind kind, SessionStatus status,
                         Instant assignedAt, List<StoredPrescription> prescriptions) {
    }

    record StoredPrescription(UUID id, UUID exerciseVersionId, int position,
                              Integer targetSets, Integer targetRepetitions,
                              Integer targetDurationSeconds, BigDecimal targetLoadKg,
                              String notes) {
    }
}
