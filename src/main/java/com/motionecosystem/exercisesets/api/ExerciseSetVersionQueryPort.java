package com.motionecosystem.exercisesets.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Read-only, API-neutral planning boundary for one exact versioned exercise-set snapshot. */
public interface ExerciseSetVersionQueryPort {
    Optional<ExerciseSetVersionSnapshot> findById(UUID exerciseSetVersionId);

    record ExerciseSetVersionSnapshot(UUID exerciseSetId, UUID exerciseSetVersionId, int versionNumber,
                                      String status, UUID ownerAccountId, String title, String profile,
                                      String description, String targetLevel, List<String> tags,
                                      Instant createdAt, Instant publishedAt, String analysisJson,
                                      String anatomyAnalysisJson, List<ItemSnapshot> items) {
        public ExerciseSetVersionSnapshot {
            tags = tags == null ? List.of() : List.copyOf(tags);
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ItemSnapshot(UUID itemId, UUID exerciseVersionId, int position, String phase,
                        ExerciseSnapshot exercise, DoseSnapshot dose,
                        String participantInstruction, String specialistInstruction) { }

    record ExerciseSnapshot(String canonicalName, int versionNumber, int profileSchemaVersion,
                            List<String> movementPatterns, List<String> requiredEquipment) {
        public ExerciseSnapshot {
            movementPatterns = movementPatterns == null ? List.of() : List.copyOf(movementPatterns);
            requiredEquipment = requiredEquipment == null ? List.of() : List.copyOf(requiredEquipment);
        }
    }

    sealed interface DoseSnapshot permits StrengthDoseSnapshot, IsometricDoseSnapshot,
            MobilityDoseSnapshot, StretchDoseSnapshot, BreathingDoseSnapshot, AerobicDoseSnapshot {
        @JsonProperty("type")
        String type();
    }

    record StrengthDoseSnapshot(Integer sets, Integer reps, Integer repMin, Integer repMax,
                               Integer restSeconds, String tempo, BigDecimal loadValue, String loadUnit,
                               BigDecimal rpe, Integer rir, String side) implements DoseSnapshot {
        @Override public String type() { return "STRENGTH"; }
    }
    record IsometricDoseSnapshot(Integer sets, Integer holdSeconds, Integer restSeconds,
                                 String intensity, String side) implements DoseSnapshot {
        @Override public String type() { return "ISOMETRIC"; }
    }
    record MobilityDoseSnapshot(Integer reps, Integer durationSeconds, String rangeTarget,
                                String side, String tempo) implements DoseSnapshot {
        @Override public String type() { return "MOBILITY"; }
    }
    record StretchDoseSnapshot(Integer holdSeconds, Integer repetitions, String side,
                               String intensity) implements DoseSnapshot {
        @Override public String type() { return "STRETCH"; }
    }
    record BreathingDoseSnapshot(Integer durationSeconds, Integer cycles, String rhythm) implements DoseSnapshot {
        @Override public String type() { return "BREATHING"; }
    }
    record AerobicDoseSnapshot(Integer durationSeconds, Integer distanceMeters, String intensity,
                               String zone, BigDecimal rpe) implements DoseSnapshot {
        @Override public String type() { return "AEROBIC"; }
    }
}
