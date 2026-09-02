package com.motionecosystem.exerciseimport;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exerciseimport.api.CreateExerciseDraft;
import com.motionecosystem.exerciseimport.api.FindExerciseMatch;
import com.motionecosystem.exerciseimport.api.ImportArtifactStorage;
import com.motionecosystem.exerciseimport.api.NormalizeImportRecord;
import com.motionecosystem.exerciseimport.api.ValidateImportRecord;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseImportServiceTest {

    @Test
    void reusingIdempotencyKeyWithDifferentContentIsRejectedAfterContentHashIsComputed() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        ExerciseImportSourceRepository sources = mock(ExerciseImportSourceRepository.class);
        ExerciseImportBatchRepository batches = mock(ExerciseImportBatchRepository.class);
        ExerciseImportArtifactRepository artifactsRepository = mock(ExerciseImportArtifactRepository.class);
        ImportArtifactStorage artifactStorage = mock(ImportArtifactStorage.class);
        ExerciseImportBatchEntity existingBatch = batch(sourceId, batchId);
        ExerciseImportArtifactEntity existingArtifact = artifact(batchId, "existing-sha");
        when(sources.findById(sourceId)).thenReturn(Optional.of(new ExerciseImportSourceEntity()));
        when(batches.findBySourceIdAndRequestKey(sourceId, "request-key")).thenReturn(Optional.of(existingBatch));
        when(artifactsRepository.findByBatchId(batchId)).thenReturn(Optional.of(existingArtifact));
        when(artifactStorage.store(any(), any(), any(), anyLong()))
                .thenReturn(new ImportArtifactStorage.StoredArtifact("transient-key", 7, "new-sha"));

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service(sources, batches, artifactsRepository, artifactStorage)
                        .upload("editor", sourceId, "request-key", false, file("new")),
                ResponseStatusException.class);

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("Idempotency-Key was already used with different content");
        verify(artifactStorage).delete("transient-key");
    }

    @Test
    void reusingIdempotencyKeyWithSameContentReturnsExistingBatch() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        ExerciseImportSourceRepository sources = mock(ExerciseImportSourceRepository.class);
        ExerciseImportBatchRepository batches = mock(ExerciseImportBatchRepository.class);
        ExerciseImportArtifactRepository artifactsRepository = mock(ExerciseImportArtifactRepository.class);
        ImportArtifactStorage artifactStorage = mock(ImportArtifactStorage.class);
        ExerciseImportBatchEntity existingBatch = batch(sourceId, batchId);
        ExerciseImportArtifactEntity existingArtifact = artifact(batchId, "same-sha");
        when(sources.findById(sourceId)).thenReturn(Optional.of(new ExerciseImportSourceEntity()));
        when(batches.findBySourceIdAndRequestKey(sourceId, "request-key")).thenReturn(Optional.of(existingBatch));
        when(artifactsRepository.findByBatchId(batchId)).thenReturn(Optional.of(existingArtifact));
        when(artifactStorage.store(any(), any(), any(), anyLong()))
                .thenReturn(new ImportArtifactStorage.StoredArtifact("transient-key", 7, "same-sha"));

        ExerciseImportService.BatchView result = service(sources, batches, artifactsRepository, artifactStorage)
                .upload("editor", sourceId, "request-key", false, file("same"));

        assertThat(result.id()).isEqualTo(batchId);
        verify(artifactStorage).delete("transient-key");
    }

    @Test
    void pagedRecordsQueryHasStableLegacyOrder() throws NoSuchMethodException {
        Method method = ExerciseImportRecordRepository.class.getDeclaredMethod(
                "findPage", UUID.class, String.class, String.class, org.springframework.data.domain.Pageable.class);

        assertThat(method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value())
                .contains("order by record.rowNumber, record.id");
    }

    private static ExerciseImportService service(ExerciseImportSourceRepository sources,
                                                 ExerciseImportBatchRepository batches,
                                                 ExerciseImportArtifactRepository artifactsRepository,
                                                 ImportArtifactStorage artifactStorage) {
        return new ExerciseImportService(sources, batches, artifactsRepository,
                mock(ExerciseImportRecordRepository.class), mock(ExerciseImportMappingRepository.class),
                mock(ExerciseImportIssueRepository.class), mock(ExerciseImportMatchCandidateRepository.class),
                mock(ImportCatalogReadRepository.class), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-02T10:00:00Z"), ZoneOffset.UTC), mock(TransactionTemplate.class),
                artifactStorage, mock(ExerciseImportJobLauncher.class), mock(NormalizeImportRecord.class),
                mock(ValidateImportRecord.class), mock(FindExerciseMatch.class), mock(CreateExerciseDraft.class),
                mock(AuditRecorder.class));
    }

    private static ExerciseImportBatchEntity batch(UUID sourceId, UUID batchId) {
        ExerciseImportBatchEntity batch = new ExerciseImportBatchEntity();
        batch.id = batchId;
        batch.sourceId = sourceId;
        batch.requestKey = "request-key";
        batch.status = "QUEUED";
        return batch;
    }

    private static ExerciseImportArtifactEntity artifact(UUID batchId, String sha256) {
        ExerciseImportArtifactEntity artifact = new ExerciseImportArtifactEntity();
        artifact.batchId = batchId;
        artifact.sha256 = sha256;
        return artifact;
    }

    private static MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "fixture.jsonl", "application/x-ndjson", content.getBytes());
    }
}
