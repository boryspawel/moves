package com.motionecosystem.exercisecatalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable catalog boundary used by planning and load calculation. */
public interface ExerciseCatalogQueryPort {

    Optional<PublishedExerciseVersionSnapshot> findPublishedVersion(UUID versionId);

    Map<UUID, PublishedExerciseVersionSnapshot> findPublishedVersions(Set<UUID> versionIds);

    record PublishedExerciseVersionSnapshot(
            UUID exerciseId,
            String canonicalName,
            UUID versionId,
            int versionNumber,
            int profileSchemaVersion,
            Set<MovementPatternValue> movementPatterns,
            Set<String> requiredEquipment,
            List<ContributionSnapshot> contributions,
            List<LoadCharacteristicSnapshot> loadCharacteristics) {
        public PublishedExerciseVersionSnapshot {
            movementPatterns = Set.copyOf(movementPatterns);
            requiredEquipment = Set.copyOf(requiredEquipment);
            contributions = List.copyOf(contributions);
            loadCharacteristics = List.copyOf(loadCharacteristics);
        }
    }

    record ContributionSnapshot(
            UUID id,
            UUID anatomicalStructureId,
            ContributionRoleValue role,
            LoadChannelValue loadChannel,
            ContributionBandValue contributionBand,
            BigDecimal coefficientLow,
            BigDecimal coefficientHigh,
            String confidenceClass,
            String evidenceGrade,
            CalculationRoleValue calculationRole,
            String variantCondition,
            SideRuleValue sideRule,
            List<EvidenceSnapshot> evidence) {
        public ContributionSnapshot {
            evidence = List.copyOf(evidence);
        }
    }

    record EvidenceSnapshot(UUID id, String citation, String sourceUri, String evidenceGrade) {
    }

    record LoadCharacteristicSnapshot(
            UUID id,
            MovementPlaneValue movementPlane,
            ContractionTypeValue contractionType,
            RangeOfMotionValue rangeOfMotion,
            LoadCharacteristicValue characteristicType) {
    }

    enum MovementPatternValue { SQUAT, HINGE, PUSH, PULL, LUNGE, CARRY, ROTATION, LOCOMOTION, BREATHING, MOBILITY, OTHER }
    enum ContributionRoleValue { PRIMARY, SECONDARY, STABILIZER }
    enum LoadChannelValue { DYN_EXU, ISO_SEC, IMPACT_CONTACTS, ENDURANCE_MIN_ZONE }
    enum ContributionBandValue { LOW, MODERATE, HIGH }
    enum CalculationRoleValue { ALLOCATION, DESCRIPTIVE_ONLY }
    enum SideRuleValue { AS_PRESCRIBED, BILATERAL, LEFT, RIGHT, NOT_APPLICABLE }
    enum MovementPlaneValue { SAGITTAL, FRONTAL, TRANSVERSE, MULTIPLANAR }
    enum ContractionTypeValue { CONCENTRIC, ECCENTRIC, ISOMETRIC, MIXED }
    enum RangeOfMotionValue { PARTIAL, FULL, VARIABLE }
    enum LoadCharacteristicValue { DYNAMIC, ISOMETRIC, ECCENTRIC_EMPHASIS, IMPACT, COMPRESSION, SHEAR, ROTATION, STABILIZATION }
}
