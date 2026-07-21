package com.motionecosystem.trainingplanning.api;

import java.util.List;
import java.util.UUID;

public interface TrainingPlanningWorkflowPort {

    StructuralValidationSnapshot validateForWorkflow(
            String actorSubject, UUID revisionId, long expectedVersion);

    record StructuralValidationSnapshot(boolean passed, List<String> violations) {
        public StructuralValidationSnapshot {
            violations = List.copyOf(violations);
        }
    }
}
