package com.motionecosystem.exercisecatalog;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImportedExerciseJpaValueObjectsTest {
    private final UUID versionId = UUID.randomUUID();

    @Test
    void embeddedIdentifiersCompareAllPrimaryKeyFields() {
        assertIdentifierContract(purposeId("STRENGTH"), purposeId("STRENGTH"), purposeId("MOBILITY"));
        assertIdentifierContract(equipmentId("DUMBBELL"), equipmentId("DUMBBELL"), equipmentId("BAND"));
        assertIdentifierContract(doseCapabilityId("REPETITIONS"), doseCapabilityId("REPETITIONS"), doseCapabilityId("SECONDS"));
    }

    private void assertIdentifierContract(Object equalLeft, Object equalRight, Object different) {
        assertThat(equalLeft).isEqualTo(equalRight).hasSameHashCodeAs(equalRight).isNotEqualTo(different);
        assertThat(new HashSet<>(java.util.List.of(equalLeft, equalRight, different))).hasSize(2);
    }

    private ImportedExerciseVersionPurposeId purposeId(String purpose) {
        var id = new ImportedExerciseVersionPurposeId();
        id.versionId = versionId;
        id.purpose = purpose;
        return id;
    }

    private ImportedExerciseEquipmentId equipmentId(String equipmentCode) {
        var id = new ImportedExerciseEquipmentId();
        id.versionId = versionId;
        id.equipmentCode = equipmentCode;
        return id;
    }

    private ImportedExerciseDoseCapabilityId doseCapabilityId(String unitCode) {
        var id = new ImportedExerciseDoseCapabilityId();
        id.versionId = versionId;
        id.unitCode = unitCode;
        return id;
    }
}
