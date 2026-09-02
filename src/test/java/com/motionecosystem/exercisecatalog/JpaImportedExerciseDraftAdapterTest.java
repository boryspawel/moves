package com.motionecosystem.exercisecatalog;

import com.motionecosystem.exercisecatalog.api.ImportedExerciseDraftPort;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class JpaImportedExerciseDraftAdapterTest {
    @Test
    void persistsSourceLicenseAndRecordKeyOnImportEvidence() {
        ExerciseRepository exercises = mock(ExerciseRepository.class);
        ExerciseVersionRepository versions = mock(ExerciseVersionRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        UUID exerciseId = UUID.randomUUID();
        Exercise exercise = new Exercise();
        exercise.id = exerciseId;
        when(exercises.findLockedById(exerciseId)).thenReturn(Optional.of(exercise));
        when(versions.findFirstByExerciseIdOrderByVersionNumberDesc(exerciseId)).thenReturn(Optional.empty());
        when(versions.saveAndFlush(any(ExerciseVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        JpaImportedExerciseDraftAdapter adapter = new JpaImportedExerciseDraftAdapter(exercises, versions, entityManager,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        adapter.create(command(exerciseId));

        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce()).persist(persisted.capture());
        ImportedEvidenceSource evidence = persisted.getAllValues().stream()
                .filter(ImportedEvidenceSource.class::isInstance)
                .map(ImportedEvidenceSource.class::cast)
                .findFirst().orElseThrow();
        assertThat(evidence.licenseCode).isEqualTo("CC0-1.0");
        assertThat(evidence.citation).isEqualTo("Import source record squat-source-001");
    }

    private ImportedExerciseDraftPort.ImportedExerciseDraft command(UUID exerciseId) {
        return new ImportedExerciseDraftPort.ImportedExerciseDraft(exerciseId, UUID.randomUUID(), UUID.randomUUID(),
                "CC0-1.0", "squat-source-001", "importer", "Squat", "en", "hash", "Brace and squat",
                "SQUAT", "STRENGTH", "MODERATE", "FOUNDATIONAL", "GYM", List.of("TRAINING"),
                List.of(), List.of("SQUAT"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
