package com.motionecosystem.exercisecatalog;

import com.motionecosystem.exercisecatalog.api.ImportedExerciseDraftPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** JPA-only writer behind the import boundary; import code never reaches catalog tables directly. */
@Repository
@RequiredArgsConstructor
class JpaImportedExerciseDraftAdapter implements ImportedExerciseDraftPort {
    private final ExerciseRepository exercises;
    private final ExerciseVersionRepository versions;
    private final ImportedExerciseAliasRepository aliases;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override @Transactional
    public UUID create(ImportedExerciseDraft command) {
        Instant now = clock.instant();
        UUID exerciseId = command.existingExerciseId();
        if (exerciseId == null) {
            exerciseId = exercises.save(new Exercise(command.canonicalName(), command.actorSubject(), now)).id;
        } else {
            exercises.findLockedById(exerciseId).orElseThrow(() -> new IllegalStateException("exercise not found"));
        }
        int number = versions.findFirstByExerciseIdOrderByVersionNumberDesc(exerciseId)
                .map(value -> value.versionNumber + 1).orElse(1);
        ExerciseVersion version = new ExerciseVersion(exerciseId, number, new CatalogService.VersionCommand(
                command.instruction(), null, command.movementPatterns().stream().map(MovementPattern::valueOf).collect(java.util.stream.Collectors.toSet()),
                StimulusType.valueOf(command.stimulusType()), FatigueProfile.valueOf(command.fatigueProfile()),
                TechnicalLevel.valueOf(command.technicalLevel()), ExerciseEnvironment.valueOf(command.environment()),
                new java.util.LinkedHashSet<>(command.equipment())), now);
        version.markImported(command.importRecordId(), command.semanticSha256());
        versions.saveAndFlush(version);
        persistChildren(command, version.id, exerciseId, now);
        return version.id;
    }

    private void persistChildren(ImportedExerciseDraft command, UUID versionId, UUID exerciseId, Instant now) {
        command.purposes().forEach(value -> { var item = new ImportedExerciseVersionPurpose(); item.id = new ImportedExerciseVersionPurposeId(); item.id.versionId = versionId; item.id.purpose = value; item.sourceId = command.sourceId(); entityManager.persist(item); });
        var text = new ImportedExerciseVersionText(); text.id = UUID.randomUUID(); text.versionId = versionId; text.locale = command.locale(); text.name = command.canonicalName(); text.sourceId = command.sourceId(); entityManager.persist(text);
        command.instructionSteps().forEach(value -> { var item = new ImportedExerciseInstructionStep(); item.id = UUID.randomUUID(); item.versionId = versionId; item.locale = command.locale(); item.stepNumber = value.number(); item.instruction = value.instruction(); item.sourceId = command.sourceId(); entityManager.persist(item); });
        java.util.LinkedHashSet<String> aliases = new java.util.LinkedHashSet<>(command.aliases()); aliases.add(command.canonicalName()); aliases.forEach(value -> persistAliasIfAbsent(command, exerciseId, value));
        command.movementCharacteristics().forEach(value -> { var item = new ImportedExerciseMovementCharacteristic(); item.id = UUID.randomUUID(); item.versionId = versionId; item.movementPattern = value.movementPattern(); item.positionCode = value.position(); item.unilateral = value.unilateral(); item.loadNature = value.loadNature(); item.sourceId = command.sourceId(); entityManager.persist(item); });
        command.equipment().forEach(value -> { var item = new ImportedExerciseEquipment(); item.id = new ImportedExerciseEquipmentId(); item.id.versionId = versionId; item.id.equipmentCode = value; item.required = true; item.sourceId = command.sourceId(); entityManager.persist(item); });
        command.doseCapabilities().forEach(value -> { var item = new ImportedExerciseDoseCapability(); item.id = new ImportedExerciseDoseCapabilityId(); item.id.versionId = versionId; item.id.unitCode = value.unit(); item.minimum = value.minimum(); item.maximum = value.maximum(); item.sourceId = command.sourceId(); entityManager.persist(item); });
        command.loadCharacteristics().forEach(value -> entityManager.persist(new ExerciseLoadCharacteristic(versionId,
                MovementPlane.valueOf(value.movementPlane()), ContractionType.valueOf(value.contractionType()),
                RangeOfMotion.valueOf(value.rangeOfMotion()), LoadCharacteristicType.valueOf(value.characteristicType()), command.actorSubject(), now)));
        UUID evidenceId = UUID.randomUUID(); var evidence = new ImportedEvidenceSource(); evidence.id = evidenceId; evidence.versionId = versionId; evidence.citation = "Import source record " + command.sourceRecordKey(); evidence.evidenceGrade = "SOURCE_ASSERTION"; evidence.createdAt = now; evidence.createdBySubject = command.actorSubject(); evidence.sourceType = "IMPORT"; evidence.licenseCode = command.sourceLicenseCode(); evidence.sourceId = command.sourceId(); entityManager.persist(evidence);
        command.contributions().forEach(value -> { var contribution = new ExerciseContribution(versionId, value.anatomicalStructureId(), ContributionRole.valueOf(value.role()), LoadChannel.valueOf(value.loadChannel()), ContributionBand.valueOf(value.band()), value.coefficientLow(), value.coefficientHigh(), "SOURCE_ASSERTION", "SOURCE_ASSERTION", CalculationRole.ALLOCATION, null, ContributionSideRule.valueOf(value.sideRule()), command.actorSubject(), now); entityManager.persist(contribution); entityManager.persist(new ExerciseContributionEvidence(contribution.id, evidenceId)); });
        var link = new ImportedExerciseEvidenceLink(); link.id = UUID.randomUUID(); link.versionId = versionId; link.evidenceSourceId = evidenceId; link.claimType = "ANATOMY_EXPOSURE"; link.jsonPointer = "/contributions"; entityManager.persist(link);
    }

    private void persistAliasIfAbsent(ImportedExerciseDraft command, UUID exerciseId, String value) {
        String normalizedAlias = value.trim().toLowerCase(Locale.ROOT);
        if (aliases.existsByExerciseIdAndLocaleAndNormalizedAlias(exerciseId, command.locale(), normalizedAlias)) {
            return;
        }
        var item = new ImportedExerciseAlias();
        item.id = UUID.randomUUID();
        item.exerciseId = exerciseId;
        item.locale = command.locale();
        item.alias = value;
        item.normalizedAlias = normalizedAlias;
        item.sourceId = command.sourceId();
        entityManager.persist(item);
    }
}
