package com.motionecosystem.loadanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.Aggregate;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.Observation;
import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class LoadAnalysisPersistenceIntegrationTest {
    @Autowired LoadAnalysisPersistence persistence;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE load_analysis.planned_load_snapshot, load_analysis.load_calculation_version, training_planning.training_plan CASCADE");
    }

    @Test
    @Transactional
    void snapshotRoundTripIsIdempotentlyAddressedAndAlgorithmVersionChangesIdentity() {
        UUID revisionId = revision();
        LoadProfile first = profile(revisionId, "algorithm-v1", UUID.randomUUID());
        persistence.save(first);

        LoadProfile restored = persistence.find(revisionId, "checksum", "algorithm-v1", "config-v1")
                .orElseThrow();
        assertThat(restored.snapshotId()).isEqualTo(first.snapshotId());
        assertThat(restored.observations()).usingRecursiveComparison().isEqualTo(first.observations());
        assertThat(restored.aggregates()).usingRecursiveComparison().isEqualTo(first.aggregates());

        LoadProfile second = profile(revisionId, "algorithm-v2", UUID.randomUUID());
        persistence.save(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM load_analysis.planned_load_snapshot", Long.class))
                .isEqualTo(2);
    }

    private UUID revision() {
        UUID plan = UUID.randomUUID(); UUID revision = UUID.randomUUID(); UUID participant = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_planning.training_plan
                    (id, participant_account_id, created_by_account_id, name, plan_mode, status,
                     created_at, purpose, owner_account_id, version)
                VALUES (?, ?, ?, 'Load plan', 'SELF_DIRECTED', 'DRAFT', now(), 'Load test', ?, 0)
                """, plan, participant, author, author);
        jdbc.update("""
                INSERT INTO training_planning.plan_revision
                    (id, plan_id, revision_number, status, phase_intent, author_account_id,
                     author_capability, migration_origin, assessment_status,
                     draft_updated_at, created_at, version)
                VALUES (?, ?, 1, 'DRAFT', 'Load phase', ?, 'SELF_DIRECTED',
                        'NATIVE_V2', 'NOT_ASSESSED', now(), now(), 0)
                """, revision, plan, author);
        jdbc.update("UPDATE training_planning.training_plan SET current_revision_id=? WHERE id=?", revision, plan);
        return revision;
    }

    private static LoadProfile profile(UUID revision, String algorithm, UUID snapshot) {
        UUID prescription = UUID.randomUUID(); UUID exercise = UUID.randomUUID();
        UUID contribution = UUID.randomUUID(); UUID session = UUID.randomUUID();
        UUID micro = UUID.randomUUID(); UUID cycle = UUID.randomUUID(); UUID structure = UUID.randomUUID();
        Observation observation = new Observation(prescription, exercise, contribution, session, micro, cycle,
                structure, "LEFT", "DYN_EXU", "DIRECT_ALLOCATION", "EXU",
                new BigDecimal("1.250000"), new BigDecimal("2.500000"), "MEDIUM", "B",
                "SETS_X_REPETITIONS", "CATALOG_INTERVAL");
        Aggregate aggregate = new Aggregate("SESSION", session.toString(), structure, "LEFT", "DYN_EXU",
                "DIRECT_ALLOCATION", "EXU", new BigDecimal("1.250000"), new BigDecimal("2.500000"));
        return new LoadProfile(snapshot, revision, "checksum", algorithm, "config-v1", "v2",
                Instant.parse("2026-07-21T10:00:00Z"), List.of(observation), List.of(aggregate));
    }
}
