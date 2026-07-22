package com.motionecosystem.trainingplanning;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motionecosystem.trainingplanning.TrainingPlanningModel.Cycle;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.Goal;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.GoalOutcome;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.LoadBudget;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.MicrocycleV2;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanDraft;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.Prescription;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.Revision;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.Session;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.SessionVariant;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.SessionVariantItem;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.StructuralValidation;

public interface TrainingPlanningV2Persistence {

    void createDraft(PlanDraft plan, Revision revision);

    void addGoal(UUID revisionId, long expectedVersion, Goal goal,
                 List<GoalOutcome> outcomes, Instant updatedAt);

    void addCycle(UUID revisionId, long expectedVersion, Cycle cycle, Instant updatedAt);

    void addMicrocycle(UUID revisionId, long expectedVersion,
                       MicrocycleV2 microcycle, Instant updatedAt);

    void addSession(UUID revisionId, long expectedVersion, Session session, Instant updatedAt);

    void addPrescription(UUID revisionId, long expectedVersion,
                         Prescription prescription, Instant updatedAt);

    void reorderPrescriptions(UUID revisionId, long expectedVersion, UUID sessionId,
                              List<UUID> orderedPrescriptionIds, Instant updatedAt);

    void defineSessionVariant(UUID revisionId, long expectedVersion, SessionVariant variant,
                              List<SessionVariantItem> items, Instant updatedAt);

    void addLoadBudget(UUID revisionId, long expectedVersion,
                       LoadBudget budget, Instant updatedAt);

    UUID cloneRevision(UUID planId, UUID basedOnRevisionId, UUID authorAccountId,
                       String authorCapability, Instant now);

    void saveStructuralValidation(StructuralValidation validation);

    Optional<PlanAccess> findPlanAccess(UUID planId);

    Optional<RevisionAccess> findRevisionAccess(UUID revisionId);

    List<RevisionHistoryItem> revisionHistory(UUID planId);

    record PlanAccess(UUID planId, UUID participantAccountId, String name, String purpose,
                      UUID ownerAccountId, String mode, String status, UUID currentRevisionId,
                      String ownerCapability) {
    }

    record RevisionAccess(UUID revisionId, UUID planId, UUID participantAccountId,
                          UUID ownerAccountId, String mode, String status,
                          int revisionNumber, long version, String authorCapability) {
    }

    record RevisionHistoryItem(UUID revisionId, int revisionNumber, UUID basedOnRevisionId,
                               String status, String migrationOrigin, String assessmentStatus,
                               long version, Instant createdAt) {
    }

    final class RevisionConflictException extends RuntimeException {
    }

    final class ImmutableRevisionException extends RuntimeException {
    }
}
