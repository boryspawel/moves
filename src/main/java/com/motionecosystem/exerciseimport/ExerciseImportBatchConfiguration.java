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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Queue;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@RequiredArgsConstructor
class ExerciseImportBatchConfiguration {
    private static final int CHUNK_SIZE = 50;
    private final JdbcTemplate jdbc;
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
            jdbc.update("UPDATE exercise_import.import_batch SET status='PROCESSING',started_at=COALESCE(started_at,?),version=version+1 WHERE id=?",
                    Timestamp.from(clock.instant()), batchId);
            return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;
        }, transactions).build();
    }

    @Bean("parseExerciseImport")
    Step parse(JobRepository repository, PlatformTransactionManager transactions,
               @Value("${exercise-import.limits.record-bytes}") long recordLimit) {
        return new StepBuilder("PARSE", repository).tasklet((contribution, context) -> {
            UUID batchId = batchId(context.getStepContext().getJobParameters().get("batchId"));
            String storageKey = jdbc.queryForObject(
                    "SELECT storage_key FROM exercise_import.import_artifact WHERE batch_id=?", String.class, batchId);
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
            for (UUID recordId : jdbc.queryForList("""
                    SELECT id FROM exercise_import.import_record
                    WHERE batch_id=? AND status='READY_FOR_DRAFT' AND draft_version_id IS NULL
                    ORDER BY row_number,id
                    """, UUID.class, batchId)) {
                try {
                    perRecord.executeWithoutResult(status -> drafts.createDraft(recordId, "exercise-import-batch"));
                } catch (RuntimeException failure) {
                    draftIssue(batchId, recordId);
                }
            }
            refresh(batchId);
            return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;
        }, transactions).build();
    }

    @Bean("normalizeImportReader") @StepScope
    ItemReader<UUID> normalizeReader(@Value("#{jobParameters['batchId']}") String id) {
        return new StatusItemReader(jdbc, UUID.fromString(id), "PARSED");
    }
    @Bean("validateImportReader") @StepScope
    ItemReader<UUID> validateReader(@Value("#{jobParameters['batchId']}") String id) {
        return new StatusItemReader(jdbc, UUID.fromString(id), "NORMALIZED");
    }
    @Bean("matchImportReader") @StepScope
    ItemReader<UUID> matchReader(@Value("#{jobParameters['batchId']}") String id) {
        return new StatusItemReader(jdbc, UUID.fromString(id), "NORMALIZED");
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
                    jdbc.update("UPDATE exercise_import.import_batch SET status='FAILED',completed_at=?,version=version+1 WHERE id=?",
                            Timestamp.from(clock.instant()), batchId);
                    batchIssue(batchId, "BATCH_JOB_FAILED", "BLOCKER", "Pipeline Spring Batch zakończył się błędem; job można wznowić.");
                } else refresh(batchId);
            }
        };
    }

    private void refresh(UUID batchId) {
        jdbc.update("""
                UPDATE exercise_import.import_batch batch SET
                    total_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=?),
                    valid_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status NOT IN ('INVALID','BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','REJECTED')),
                    invalid_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status='INVALID'),
                    blocked_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND (r.status IN ('BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','MATCH_CANDIDATES') OR EXISTS (SELECT 1 FROM exercise_import.import_issue i WHERE i.record_id=r.id AND i.code='DRAFT_CREATION_FAILED' AND i.resolved_at IS NULL))),
                    drafted_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status='DRAFTED'),
                    unchanged_count=(SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status='UNCHANGED'),
                    status=CASE WHEN batch.status='FAILED' THEN 'FAILED'
                        WHEN NOT EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('RECEIVED','PARSED','NORMALIZED'))
                         AND (EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('INVALID','BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','MATCH_CANDIDATES')) OR EXISTS (SELECT 1 FROM exercise_import.import_issue i WHERE i.batch_id=? AND i.severity IN ('ERROR','BLOCKER') AND i.resolved_at IS NULL)) THEN 'COMPLETED_WITH_ISSUES'
                        WHEN NOT EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('RECEIVED','PARSED','NORMALIZED')) THEN 'COMPLETED'
                        ELSE 'PROCESSING' END,
                    completed_at=CASE WHEN EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? ) AND NOT EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=? AND r.status IN ('RECEIVED','PARSED','NORMALIZED')) THEN COALESCE(batch.completed_at,?) ELSE NULL END,
                    version=batch.version+1 WHERE batch.id=?
                """, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, batchId, Timestamp.from(clock.instant()), batchId);
    }

    private void insertRecord(UUID batchId, long row, String payload, String hash) {
        InstantPair now = new InstantPair(Timestamp.from(clock.instant()));
        jdbc.update("""
                INSERT INTO exercise_import.import_record(id,batch_id,row_number,status,raw_payload,raw_sha256,created_at,updated_at,version)
                VALUES (?, ?,?,'PARSED',CAST(? AS jsonb),?,?,?,0) ON CONFLICT(batch_id,row_number) DO NOTHING
                """, UUID.randomUUID(), batchId, row, payload, hash, now.value, now.value);
    }

    private void insertInvalid(UUID batchId, long row, String payload, String hash,
                               String code, String pointer, String message) {
        UUID id = UUID.randomUUID(); var now = Timestamp.from(clock.instant());
        int inserted = jdbc.update("""
                INSERT INTO exercise_import.import_record(id,batch_id,row_number,status,raw_payload,raw_sha256,created_at,updated_at,version)
                VALUES (?, ?,?,'INVALID',CAST(? AS jsonb),?,?,?,0) ON CONFLICT(batch_id,row_number) DO NOTHING
                """, id, batchId, row, payload, hash, now, now);
        if (inserted > 0) jdbc.update("""
                INSERT INTO exercise_import.import_issue(id,batch_id,record_id,row_number,code,stage,severity,json_pointer,message,created_at)
                VALUES (?,?,?,?,?,'PARSE','ERROR',?,?,?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), batchId, id, row, code, pointer, message, now);
    }

    private boolean recordExists(UUID batchId, long row) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM exercise_import.import_record WHERE batch_id=? AND row_number=?)",
                Boolean.class, batchId, row));
    }

    private void batchIssue(UUID batchId, String code, String severity, String message) {
        jdbc.update("""
                INSERT INTO exercise_import.import_issue(id,batch_id,code,stage,severity,json_pointer,message,created_at)
                VALUES (?,?,?,'PARSE',?,'/',?,?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), batchId, code, severity, message, Timestamp.from(clock.instant()));
    }

    private void draftIssue(UUID batchId, UUID recordId) {
        jdbc.update("""
                INSERT INTO exercise_import.import_issue(id,batch_id,record_id,row_number,code,stage,severity,json_pointer,message,created_at)
                SELECT ?,?,?,row_number,'DRAFT_CREATION_FAILED','CREATE_DRAFT','ERROR','/',
                       'Nie można było utworzyć szkicu. Rekord pozostaje gotowy do bezpiecznego ponowienia.',?
                FROM exercise_import.import_record record WHERE id=?
                  AND NOT EXISTS (SELECT 1 FROM exercise_import.import_issue issue
                                  WHERE issue.record_id=record.id AND issue.code='DRAFT_CREATION_FAILED'
                                    AND issue.resolved_at IS NULL)
                """, UUID.randomUUID(), batchId, recordId, Timestamp.from(clock.instant()), recordId);
    }

    private static UUID batchId(Object value) { return UUID.fromString(String.valueOf(value)); }
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @FunctionalInterface private interface RecordOperation { void apply(UUID id); }
    private record InstantPair(Timestamp value) { }
    private static final class StatusItemReader implements ItemReader<UUID> {
        private final Queue<UUID> ids;
        private StatusItemReader(JdbcTemplate jdbc, UUID batchId, String status) {
            ids = new ArrayDeque<>(jdbc.queryForList(
                    "SELECT id FROM exercise_import.import_record WHERE batch_id=? AND status=? ORDER BY row_number,id",
                    UUID.class, batchId, status));
        }
        @Override public UUID read() { return ids.poll(); }
    }
}
