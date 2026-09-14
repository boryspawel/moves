package com.motionecosystem.exerciseimport;

import com.motionecosystem.exerciseimport.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@RequiredArgsConstructor
class ExerciseImportBatchConfiguration {
    private static final int CHUNK_SIZE = 50;
    private final ExerciseImportBatchRepository batches;
    private final ExerciseImportArtifactRepository importArtifacts;
    private final ExerciseImportRecordRepository records;
    private final ExerciseImportIssueRepository issues;
    private final ObjectMapper json;
    private final ImportArtifactStorage artifacts;
    private final NormalizeImportRecord normalizer;
    private final ValidateImportRecord validator;
    private final FindExerciseMatch matcher;
    private final CreateExerciseDraft drafts;
    private final Clock clock;

    @Bean(name = "exerciseImportExecutor")
    TaskExecutor exerciseImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("exercise-import-");
        executor.initialize();
        return executor;
    }

    @Bean
    Job exerciseImportJob(JobRepository repository,
                          @Qualifier("receiveExerciseImport") Step receive,
                          @Qualifier("parseExerciseImport") Step parse,
                          @Qualifier("normalizeExerciseImport") Step normalize,
                          @Qualifier("validateExerciseImport") Step validate,
                          @Qualifier("matchExerciseImport") Step match,
                          @Qualifier("prepareDraftExerciseImport") Step prepareDraft) {
        return new JobBuilder("exerciseImportJob", repository).listener(listener())
                .start(receive).next(parse).next(normalize).next(validate).next(match).next(prepareDraft).build();
    }

    @Bean("receiveExerciseImport")
    Step receive(JobRepository repository, PlatformTransactionManager transactions) {
        return new StepBuilder("RECEIVE", repository).tasklet((contribution, context) -> {
            UUID batchId = batchId(context.getStepContext().getJobParameters().get("batchId"));
            ExerciseImportBatchEntity batch = batch(batchId);
            batch.status = "PROCESSING";
            if (batch.startedAt == null) batch.startedAt = clock.instant();
            batches.save(batch);
            return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;
        }, transactions).build();
    }

    @Bean("parseExerciseImport")
    Step parse(JobRepository repository, PlatformTransactionManager transactions,
               @Value("${exercise-import.limits.record-bytes}") long recordLimit) {
        return new StepBuilder("PARSE", repository).tasklet((contribution, context) -> {
            UUID batchId = batchId(context.getStepContext().getJobParameters().get("batchId"));
            String storageKey = importArtifacts.findByBatchId(batchId)
                    .orElseThrow(() -> new IllegalStateException("import artifact not found"))
                    .storageKey;
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(artifacts.open(storageKey), decoder))) {
                String line; long row = 0;
                while ((line = reader.readLine()) != null) {
                    row++;
                    if (recordExists(batchId, row)) continue;
                    String rawHash = sha256(line);
                    if (line.getBytes(StandardCharsets.UTF_8).length > recordLimit) {
                        insertInvalid(batchId, row, json.writeValueAsString(line), rawHash,
                                "RECORD_TOO_LARGE", "/", "Linia przekracza limit " + recordLimit + " bajtów.");
                        continue;
                    }
                    try {
                        var tree = json.readTree(line);
                        if (tree == null) throw new IllegalArgumentException("empty JSON");
                        insertRecord(batchId, row, json.writeValueAsString(tree), rawHash);
                    } catch (Exception malformed) {
                        insertInvalid(batchId, row, json.writeValueAsString(line), rawHash,
                                "MALFORMED_JSON", "/", "Linia nie jest poprawnym dokumentem JSON.");
                    }
                }
            } catch (CharacterCodingException invalidUtf8) {
                batchIssue(batchId, "INVALID_UTF8", "BLOCKER", "Plik nie jest poprawnie zakodowany w UTF-8.");
                throw invalidUtf8;
            }
            return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;
        }, transactions).build();
    }

    @Bean("normalizeExerciseImport")
    Step normalize(JobRepository repository, PlatformTransactionManager transactions,
                   @Qualifier("normalizeImportReader") ItemReader<UUID> reader) {
        return chunkStep("NORMALIZE", reader, normalizer::normalize, repository, transactions);
    }

    @Bean("validateExerciseImport")
    Step validate(JobRepository repository, PlatformTransactionManager transactions,
                  @Qualifier("validateImportReader") ItemReader<UUID> reader) {
        return chunkStep("VALIDATE", reader, validator::validate, repository, transactions);
    }

    @Bean("matchExerciseImport")
    Step match(JobRepository repository, PlatformTransactionManager transactions,
               @Qualifier("matchImportReader") ItemReader<UUID> reader) {
        return chunkStep("MATCH", reader, matcher::findMatches, repository, transactions);
    }

    @Bean("prepareDraftExerciseImport")
    Step prepareDraft(JobRepository repository, PlatformTransactionManager transactions) {
        return new StepBuilder("CREATE_DRAFT", repository).tasklet((contribution, context) -> {
            UUID batchId = batchId(context.getStepContext().getJobParameters().get("batchId"));
            // Each record owns its transaction.  A bad record remains retryable and is visible as an
            // issue; successfully created drafts are never rolled back with an unrelated record.
            TransactionTemplate perRecord = new TransactionTemplate(transactions);
            perRecord.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            for (ExerciseImportRecordEntity record : records
                    .findByBatchIdAndStatusAndDraftVersionIdIsNullOrderByRowNumberAscIdAsc(batchId, "READY_FOR_DRAFT")) {
                try {
                    perRecord.executeWithoutResult(status -> drafts.createDraft(record.id, "exercise-import-batch"));
                } catch (RuntimeException failure) {
                    draftIssue(batchId, record.id);
                }
            }
            refresh(batchId);
            return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;
        }, transactions).build();
    }

    @Bean("normalizeImportReader") @StepScope
    ItemReader<UUID> normalizeReader(@Value("#{jobParameters['batchId']}") String id) {
        return new StatusItemReader(records, UUID.fromString(id), "PARSED");
    }
    @Bean("validateImportReader") @StepScope
    ItemReader<UUID> validateReader(@Value("#{jobParameters['batchId']}") String id) {
        return new StatusItemReader(records, UUID.fromString(id), "NORMALIZED");
    }
    @Bean("matchImportReader") @StepScope
    ItemReader<UUID> matchReader(@Value("#{jobParameters['batchId']}") String id) {
        return new StatusItemReader(records, UUID.fromString(id), "NORMALIZED");
    }

    private Step chunkStep(String name, ItemReader<UUID> reader, RecordOperation operation,
                           JobRepository repository, PlatformTransactionManager transactions) {
        return new StepBuilder(name, repository).<UUID, UUID>chunk(CHUNK_SIZE, transactions)
                .reader(reader).processor(id -> { operation.apply(id); return id; }).writer(chunk -> { })
                .faultTolerant().retry(org.springframework.dao.TransientDataAccessException.class).retryLimit(3)
                .build();
    }

    private JobExecutionListener listener() {
        return new JobExecutionListener() {
            @Override public void afterJob(JobExecution execution) {
                UUID batchId = UUID.fromString(execution.getJobParameters().getString("batchId"));
                if (execution.getStatus() == BatchStatus.FAILED) {
                    ExerciseImportBatchEntity batch = batch(batchId);
                    batch.status = "FAILED";
                    batch.completedAt = clock.instant();
                    batches.save(batch);
                    batchIssue(batchId, "BATCH_JOB_FAILED", "BLOCKER", "Pipeline Spring Batch zakończył się błędem; job można wznowić.");
                } else refresh(batchId);
            }
        };
    }

    private void refresh(UUID batchId) {
        ExerciseImportBatchEntity batch = batch(batchId);
        List<ExerciseImportRecordEntity> batchRecords = records.findByBatchId(batchId);
        List<ExerciseImportIssueEntity> batchIssues = issues.findByBatchIdOrderByRowNumberAscSeverityAscCodeAscIdAsc(batchId);
        batch.totalCount = batchRecords.size();
        batch.validCount = count(batchRecords, record -> !List.of("INVALID", "BLOCKED_BY_MAPPING", "BLOCKED_BY_LICENSE", "REJECTED").contains(record.status));
        batch.invalidCount = count(batchRecords, record -> record.status.equals("INVALID"));
        batch.blockedCount = count(batchRecords, record -> List.of("BLOCKED_BY_MAPPING", "BLOCKED_BY_LICENSE", "MATCH_CANDIDATES").contains(record.status)
                || batchIssues.stream().anyMatch(issue -> record.id.equals(issue.recordId) && issue.code.equals("DRAFT_CREATION_FAILED") && issue.resolvedAt == null));
        batch.draftedCount = count(batchRecords, record -> record.status.equals("DRAFTED"));
        batch.unchangedCount = count(batchRecords, record -> record.status.equals("UNCHANGED"));
        boolean unfinished = batchRecords.stream().anyMatch(record -> List.of("RECEIVED", "PARSED", "NORMALIZED").contains(record.status));
        boolean hasIssues = batchRecords.stream().anyMatch(record -> List.of("INVALID", "BLOCKED_BY_MAPPING", "BLOCKED_BY_LICENSE", "MATCH_CANDIDATES").contains(record.status))
                || batchIssues.stream().anyMatch(issue -> issue.resolvedAt == null && List.of("ERROR", "BLOCKER").contains(issue.severity));
        if (!batch.status.equals("FAILED")) batch.status = unfinished ? "PROCESSING" : hasIssues ? "COMPLETED_WITH_ISSUES" : "COMPLETED";
        batch.completedAt = !batchRecords.isEmpty() && !unfinished ? (batch.completedAt == null ? clock.instant() : batch.completedAt) : null;
        batches.save(batch);
    }

    private void insertRecord(UUID batchId, long row, String payload, String hash) {
        if (recordExists(batchId, row)) return;
        Instant now = clock.instant();
        ExerciseImportRecordEntity record = new ExerciseImportRecordEntity();
        record.id = UUID.randomUUID(); record.batchId = batchId; record.rowNumber = row; record.status = "PARSED";
        record.rawPayload = payload; record.rawSha256 = hash; record.createdAt = now; record.updatedAt = now;
        records.save(record);
    }

    private void insertInvalid(UUID batchId, long row, String payload, String hash,
                               String code, String pointer, String message) {
        if (recordExists(batchId, row)) return;
        Instant now = clock.instant(); UUID id = UUID.randomUUID();
        ExerciseImportRecordEntity record = new ExerciseImportRecordEntity();
        record.id = id; record.batchId = batchId; record.rowNumber = row; record.status = "INVALID";
        record.rawPayload = payload; record.rawSha256 = hash; record.createdAt = now; record.updatedAt = now;
        records.save(record);
        ExerciseImportIssueEntity issue = new ExerciseImportIssueEntity();
        issue.id = UUID.randomUUID(); issue.batchId = batchId; issue.recordId = id; issue.rowNumber = row;
        issue.code = code; issue.stage = "PARSE"; issue.severity = "ERROR"; issue.jsonPointer = pointer; issue.message = message; issue.createdAt = now;
        issues.save(issue);
    }

    private boolean recordExists(UUID batchId, long row) {
        return records.existsByBatchIdAndRowNumber(batchId, row);
    }

    private void batchIssue(UUID batchId, String code, String severity, String message) {
        if (issues.existsByBatchIdAndRecordIdIsNullAndCodeAndJsonPointer(batchId, code, "/")) return;
        ExerciseImportIssueEntity issue = new ExerciseImportIssueEntity();
        issue.id = UUID.randomUUID(); issue.batchId = batchId; issue.code = code; issue.stage = "PARSE";
        issue.severity = severity; issue.jsonPointer = "/"; issue.message = message; issue.createdAt = clock.instant();
        issues.save(issue);
    }

    private void draftIssue(UUID batchId, UUID recordId) {
        if (issues.existsByRecordIdAndCodeAndResolvedAtIsNull(recordId, "DRAFT_CREATION_FAILED")) return;
        ExerciseImportRecordEntity record = records.findById(recordId).orElseThrow();
        ExerciseImportIssueEntity issue = new ExerciseImportIssueEntity();
        issue.id = UUID.randomUUID(); issue.batchId = batchId; issue.recordId = recordId; issue.rowNumber = record.rowNumber;
        issue.code = "DRAFT_CREATION_FAILED"; issue.stage = "CREATE_DRAFT"; issue.severity = "ERROR"; issue.jsonPointer = "/";
        issue.message = "Nie można było utworzyć szkicu. Rekord pozostaje gotowy do bezpiecznego ponowienia."; issue.createdAt = clock.instant();
        issues.save(issue);
    }

    private ExerciseImportBatchEntity batch(UUID batchId) {
        return batches.findById(batchId).orElseThrow(() -> new IllegalStateException("import batch not found"));
    }

    private static int count(List<ExerciseImportRecordEntity> records, java.util.function.Predicate<ExerciseImportRecordEntity> predicate) {
        return (int) records.stream().filter(predicate).count();
    }

    private static UUID batchId(Object value) { return UUID.fromString(String.valueOf(value)); }
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @FunctionalInterface private interface RecordOperation { void apply(UUID id); }
    private static final class StatusItemReader implements ItemReader<UUID> {
        private final java.util.Iterator<ExerciseImportRecordEntity> records;
        private StatusItemReader(ExerciseImportRecordRepository repository, UUID batchId, String status) {
            records = repository.findByBatchIdAndStatusOrderByRowNumberAscIdAsc(batchId, status).iterator();
        }
        @Override public UUID read() { return records.hasNext() ? records.next().id : null; }
    }
}
