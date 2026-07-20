package com.motionecosystem.trainingplanning;

import java.math.BigDecimal;
import java.util.UUID;

public record ExercisePrescription(UUID id, UUID plannedSessionId, UUID exerciseVersionId,
                                   int position, Integer targetSets, Integer targetRepetitions,
                                   Integer targetDurationSeconds, BigDecimal targetLoadKg,
                                   String notes) {
}
