package com.motionecosystem.exerciseimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.support.PostgresTestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class MovesStarterExercisesV1IntegrationTest {

    private static final Path ARTIFACTS = temporaryDirectory();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("exercise-import.artifacts.directory", ARTIFACTS::toString);
    }

    @Autowired ExerciseImportService imports;
    @Autowired JdbcTemplate jdbc;

    @Test
    void importsAllStarterRecordsAndForcedReimportIsUnchanged() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        UUID sourceId = imports.createSource("starter-test",
                new ExerciseImportService.CreateSource(
                        "MOVES_STARTER_" + suffix,
                        "Moves starter exercises V1 test",
                        "pl-PL",
                        "MOVES-INTERNAL-AUTHORING-1.0",
                        true))
                .id();

        byte[] bytes = new ClassPathResource(
                "reference/exercises/v1/moves-starter-exercises-v1.jsonl")
                .getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "moves-starter-exercises-v1.jsonl",
                "application/x-ndjson", bytes);

        UUID first = imports.upload("starter-test", sourceId, "starter-1", false, file).id();
        await(first);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM exercise_import.import_record WHERE batch_id=?",
                Integer.class, first)).isEqualTo(120);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM exercise_import.import_issue
                 WHERE batch_id=? AND code IN (
                    'MALFORMED_JSON','UNKNOWN_ANATOMY','MAPPING_REQUIRED','LICENSE_NOT_VERIFIED')
                """, Integer.class, first)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM exercise_import.import_record
                 WHERE batch_id=? AND status='READY_FOR_DRAFT'
                """, Integer.class, first)).isEqualTo(120);

        List<UUID> recordIds = jdbc.queryForList("""
                SELECT id FROM exercise_import.import_record
                 WHERE batch_id=? ORDER BY row_number
                """, UUID.class, first);
        recordIds.forEach(id -> imports.createDraft(id, "starter-test"));

        UUID forced = imports.upload("starter-test", sourceId, "starter-2", true, file).id();
        await(forced);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM exercise_import.import_record
                 WHERE batch_id=? AND status='UNCHANGED'
                """, Integer.class, forced)).isEqualTo(120);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT draft_version_id)
                  FROM exercise_import.import_record WHERE batch_id=?
                """, Integer.class, first)).isEqualTo(120);
    }

    private void await(UUID batchId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM exercise_import.import_batch WHERE id=?",
                    String.class, batchId);
            if (!status.matches("RECEIVED|QUEUED|PROCESSING")) {
                assertThat(status).doesNotStartWith("FAILED");
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("batch did not complete");
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("moves-starter-import-");
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
