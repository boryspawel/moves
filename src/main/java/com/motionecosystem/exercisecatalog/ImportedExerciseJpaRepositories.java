package com.motionecosystem.exercisecatalog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositories deliberately remain package-private: the public import boundary is ImportedExerciseDraftPort. */
interface ImportedExerciseVersionPurposeRepository extends JpaRepository<ImportedExerciseVersionPurpose, ImportedExerciseVersionPurposeId> {}
interface ImportedExerciseVersionTextRepository extends JpaRepository<ImportedExerciseVersionText, UUID> {}
interface ImportedExerciseInstructionStepRepository extends JpaRepository<ImportedExerciseInstructionStep, UUID> {}
interface ImportedExerciseAliasRepository extends JpaRepository<ImportedExerciseAlias, UUID> {
    boolean existsByExerciseIdAndLocaleAndNormalizedAlias(UUID exerciseId, String locale, String normalizedAlias);
    boolean existsByExerciseId(UUID exerciseId);
}
interface ImportedExerciseMovementCharacteristicRepository extends JpaRepository<ImportedExerciseMovementCharacteristic, UUID> {}
interface ImportedExerciseEquipmentRepository extends JpaRepository<ImportedExerciseEquipment, ImportedExerciseEquipmentId> {}
interface ImportedExerciseDoseCapabilityRepository extends JpaRepository<ImportedExerciseDoseCapability, ImportedExerciseDoseCapabilityId> {}
interface ImportedExerciseEvidenceLinkRepository extends JpaRepository<ImportedExerciseEvidenceLink, UUID> {}
