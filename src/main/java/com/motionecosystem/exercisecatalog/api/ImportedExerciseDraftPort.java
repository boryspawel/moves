package com.motionecosystem.exercisecatalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Import-only boundary. It keeps import provenance outside the editorial HTTP contract. */
public interface ImportedExerciseDraftPort {
    UUID create(ImportedExerciseDraft command);

    record ImportedExerciseDraft(UUID existingExerciseId, UUID importRecordId, UUID sourceId,
                                 String actorSubject, String canonicalName, String locale,
                                 String semanticSha256, String instruction, String primaryMovementPattern,
                                 String stimulusType, String fatigueProfile, String technicalLevel,
                                 String environment, List<String> purposes, List<String> aliases,
                                 List<String> movementPatterns, List<String> equipment,
                                 List<InstructionStep> instructionSteps, List<DoseCapability> doseCapabilities,
                                 List<MovementCharacteristic> movementCharacteristics,
                                 List<LoadCharacteristic> loadCharacteristics,
                                 List<Contribution> contributions) {
        public ImportedExerciseDraft {
            purposes = immutable(purposes, "purposes");
            aliases = immutable(aliases, "aliases");
            movementPatterns = immutable(movementPatterns, "movementPatterns");
            equipment = immutable(equipment, "equipment");
            instructionSteps = immutable(instructionSteps, "instructionSteps");
            doseCapabilities = immutable(doseCapabilities, "doseCapabilities");
            movementCharacteristics = immutable(movementCharacteristics, "movementCharacteristics");
            loadCharacteristics = immutable(loadCharacteristics, "loadCharacteristics");
            contributions = immutable(contributions, "contributions");
        }

        private static <T> List<T> immutable(List<T> values, String field) {
            return List.copyOf(Objects.requireNonNull(values, field + " must not be null"));
        }
    }
    record InstructionStep(int number, String instruction) { }
    record DoseCapability(String unit, BigDecimal minimum, BigDecimal maximum) { }
    record MovementCharacteristic(String movementPattern, String position, boolean unilateral, String loadNature) { }
    record LoadCharacteristic(String movementPlane, String contractionType, String rangeOfMotion, String characteristicType) { }
    record Contribution(UUID anatomicalStructureId, String role, String loadChannel, String band,
                        BigDecimal coefficientLow, BigDecimal coefficientHigh, String sideRule) { }
}
