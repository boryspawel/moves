package com.motionecosystem.exerciseimport;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exercisecatalog.api.ImportedExerciseDraftPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportRecordUseCasesTest {

    @Test
    void normalizingUnknownDictionaryValueLocksItsSourceBeforeCreatingTheMapping() {
        ExerciseImportRecordRepository records = mock(ExerciseImportRecordRepository.class);
        ExerciseImportBatchRepository batches = mock(ExerciseImportBatchRepository.class);
        ExerciseImportSourceRepository sources = mock(ExerciseImportSourceRepository.class);
        ExerciseImportSourceReferenceRepository sourceReferences = mock(ExerciseImportSourceReferenceRepository.class);
        ExerciseImportMappingRepository mappings = mock(ExerciseImportMappingRepository.class);
        ExerciseImportIssueRepository issues = mock(ExerciseImportIssueRepository.class);
        ExerciseImportMatchCandidateRepository candidates = mock(ExerciseImportMatchCandidateRepository.class);
        ImportCatalogReadRepository catalog = mock(ImportCatalogReadRepository.class);
        ImportedExerciseDraftPort drafts = mock(ImportedExerciseDraftPort.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T10:00:00Z"), ZoneOffset.UTC);
        ImportRecordUseCases useCases = new ImportRecordUseCases(records, batches, sources, sourceReferences,
                mappings, issues, candidates, catalog, drafts, new ObjectMapper(), clock, audit);

        UUID recordId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ExerciseImportRecordEntity record = new ExerciseImportRecordEntity();
        record.id = recordId;
        record.batchId = batchId;
        record.rowNumber = 1;
        record.status = "PARSED";
        record.rawPayload = """
                {"schemaVersion":"moves.exercise-import/1.0","sourceRecordKey":"external-1","locale":"pl-PL",
                 "name":"Przysiad","aliases":[],"instructions":["Stań prosto"],"purposes":["TRAINING"],
                 "movementPatterns":["SQUAT"],"stimulusType":"STRENGTH","fatigueProfile":"LOW",
                 "technicalLevel":"FOUNDATIONAL","environment":"HOME","equipment":["UNKNOWN_ITEM"],
                 "doseCapabilities":[],"loadCharacteristics":[],"contributions":[]}
                """;
        ExerciseImportBatchEntity batch = new ExerciseImportBatchEntity();
        batch.id = batchId;
        batch.sourceId = sourceId;
        ExerciseImportSourceEntity source = new ExerciseImportSourceEntity();
        source.id = sourceId;
        source.defaultLocale = "pl-PL";
        source.licenseVerified = true;
        source.licenseCode = "license";

        when(records.findLockedById(recordId)).thenReturn(Optional.of(record));
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        when(sources.findLockedById(sourceId)).thenReturn(Optional.of(source));
        when(catalog.activeDictionaryContains("EQUIPMENT", "UNKNOWN_ITEM")).thenReturn(false);
        when(catalog.activeDictionaryContains("POSITION", "STANDING")).thenReturn(true);
        when(mappings.findBySourceIdAndDictionaryTypeAndSourceValue(sourceId, "EQUIPMENT", "UNKNOWN_ITEM"))
                .thenReturn(Optional.empty());

        useCases.normalize(recordId);

        verify(sources).findLockedById(sourceId);
        verify(mappings).save(any(ExerciseImportMappingEntity.class));
        verify(issues).save(any(ExerciseImportIssueEntity.class));
    }
}
