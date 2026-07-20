package com.motionecosystem.trainingexecution;

import java.math.BigDecimal;
import java.util.UUID;

public record ExerciseResult(UUID id, UUID sessionExecutionId, UUID exercisePrescriptionId,
                             Integer actualRepetitions, Integer actualDurationSeconds,
                             BigDecimal actualLoadKg) {
}
