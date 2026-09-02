package com.motionecosystem.exercisecatalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Embeddable class ImportedExerciseVersionPurposeId {
    @Column(name="exercise_version_id") UUID versionId;
    String purpose;

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ImportedExerciseVersionPurposeId that
                && Objects.equals(versionId, that.versionId) && Objects.equals(purpose, that.purpose);
    }

    @Override public int hashCode() { return Objects.hash(versionId, purpose); }
}
@Entity(name="ImportedExerciseVersionPurposeJpaEntity") @Table(name="exercise_version_purpose", schema="exercise_catalog") class ImportedExerciseVersionPurpose { @EmbeddedId ImportedExerciseVersionPurposeId id; @Column(name="provenance_source_id") UUID sourceId; }
@Entity(name="ImportedExerciseVersionTextJpaEntity") @Table(name="exercise_version_text", schema="exercise_catalog") class ImportedExerciseVersionText { @Id UUID id; @Column(name="exercise_version_id") UUID versionId; String locale; String name; @Column(name="provenance_source_id") UUID sourceId; }
@Entity(name="ImportedExerciseInstructionStepJpaEntity") @Table(name="exercise_instruction_step", schema="exercise_catalog") class ImportedExerciseInstructionStep { @Id UUID id; @Column(name="exercise_version_id") UUID versionId; String locale; @Column(name="step_number") int stepNumber; String instruction; @Column(name="provenance_source_id") UUID sourceId; }
@Entity(name="ImportedExerciseAliasJpaEntity") @Table(name="exercise_alias", schema="exercise_catalog") class ImportedExerciseAlias { @Id UUID id; @Column(name="exercise_id") UUID exerciseId; String locale; String alias; @Column(name="normalized_alias") String normalizedAlias; @Column(name="provenance_source_id") UUID sourceId; }
@Entity(name="ImportedExerciseMovementCharacteristicJpaEntity") @Table(name="exercise_movement_characteristic", schema="exercise_catalog") class ImportedExerciseMovementCharacteristic { @Id UUID id; @Column(name="exercise_version_id") UUID versionId; @Column(name="movement_pattern") String movementPattern; @Column(name="position_code") String positionCode; boolean unilateral; @Column(name="load_nature") String loadNature; @Column(name="provenance_source_id") UUID sourceId; }
@Embeddable class ImportedExerciseEquipmentId {
    @Column(name="exercise_version_id") UUID versionId;
    @Column(name="equipment_code") String equipmentCode;

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ImportedExerciseEquipmentId that
                && Objects.equals(versionId, that.versionId) && Objects.equals(equipmentCode, that.equipmentCode);
    }

    @Override public int hashCode() { return Objects.hash(versionId, equipmentCode); }
}
@Entity(name="ImportedExerciseEquipmentJpaEntity") @Table(name="exercise_equipment", schema="exercise_catalog") class ImportedExerciseEquipment { @EmbeddedId ImportedExerciseEquipmentId id; boolean required; @Column(name="provenance_source_id") UUID sourceId; }
@Embeddable class ImportedExerciseDoseCapabilityId {
    @Column(name="exercise_version_id") UUID versionId;
    @Column(name="unit_code") String unitCode;

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ImportedExerciseDoseCapabilityId that
                && Objects.equals(versionId, that.versionId) && Objects.equals(unitCode, that.unitCode);
    }

    @Override public int hashCode() { return Objects.hash(versionId, unitCode); }
}
@Entity(name="ImportedExerciseDoseCapabilityJpaEntity") @Table(name="exercise_dose_capability", schema="exercise_catalog") class ImportedExerciseDoseCapability { @EmbeddedId ImportedExerciseDoseCapabilityId id; @Column(name="minimum_value") BigDecimal minimum; @Column(name="maximum_value") BigDecimal maximum; @Column(name="provenance_source_id") UUID sourceId; }
@Entity(name="ImportedExerciseEvidenceLinkJpaEntity") @Table(name="exercise_evidence_link", schema="exercise_catalog") class ImportedExerciseEvidenceLink { @Id UUID id; @Column(name="exercise_version_id") UUID versionId; @Column(name="evidence_source_id") UUID evidenceSourceId; @Column(name="claim_type") String claimType; @Column(name="json_pointer") String jsonPointer; }
@Entity(name="ImportedEvidenceSourceJpaEntity") @Table(name="evidence_source", schema="exercise_catalog") class ImportedEvidenceSource { @Id UUID id; @Column(name="exercise_version_id") UUID versionId; String citation; @Column(name="evidence_grade") String evidenceGrade; @Column(name="created_at") Instant createdAt; @Column(name="created_by_subject") String createdBySubject; @Column(name="source_type") String sourceType; @Column(name="license_code") String licenseCode; @Column(name="provenance_source_id") UUID sourceId; }
