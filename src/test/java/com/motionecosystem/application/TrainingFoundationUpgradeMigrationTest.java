package com.motionecosystem.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class TrainingFoundationUpgradeMigrationTest {

    @Test
    void upgradesExistingOfflineAppointmentFromV005WithoutChangingItsMeaning() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:18-alpine"))) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("005")
                    .load()
                    .migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            UUID sessionId = insertLegacyOfflineAppointment(jdbc);
            UUID exerciseVersionId = insertLegacyExerciseVersion(jdbc);

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load()
                    .migrate();

            assertThat(jdbc.queryForObject("""
                    SELECT creation_source FROM training_planning.planned_session WHERE id = ?
                    """, String.class, sessionId)).isEqualTo("LEGACY_V1");
            assertThat(jdbc.queryForObject("""
                    SELECT session_kind FROM training_planning.planned_session WHERE id = ?
                    """, String.class, sessionId)).isEqualTo("OFFLINE_APPOINTMENT");
            assertThat(jdbc.queryForObject("""
                    SELECT profile_schema_version FROM exercise_catalog.exercise_version WHERE id = ?
                    """, Integer.class, exerciseVersionId)).isEqualTo(1);
            assertThat(jdbc.queryForList("""
                    SELECT movement_pattern FROM exercise_catalog.exercise_version_movement_pattern
                    WHERE exercise_version_id = ?
                    """, String.class, exerciseVersionId)).containsExactly("SQUAT");
            assertThat(jdbc.queryForList("""
                    SELECT contraindication_tag FROM exercise_catalog.exercise_version_contraindication
                    WHERE exercise_version_id = ?
                    """, String.class, exerciseVersionId)).containsExactly("LEGACY_KNEE_TAG");
        }
    }

    private static UUID insertLegacyOfflineAppointment(JdbcTemplate jdbc) {
        UUID participantId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID microcycleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_planning.training_goal
                    (id, participant_account_id, name, created_by_account_id, created_at)
                VALUES (?, ?, 'Legacy goal', ?, now())
                """, goalId, participantId, authorId);
        jdbc.update("""
                INSERT INTO training_planning.training_plan
                    (id, goal_id, participant_account_id, created_by_account_id, name,
                     plan_mode, status, created_at)
                VALUES (?, ?, ?, ?, 'Legacy plan', 'SPECIALIST_ASSIGNED', 'ACTIVE', now())
                """, planId, goalId, participantId, authorId);
        jdbc.update("""
                INSERT INTO training_planning.training_cycle (id, plan_id, sequence_number, name)
                VALUES (?, ?, 1, 'Legacy cycle')
                """, cycleId, planId);
        jdbc.update("""
                INSERT INTO training_planning.microcycle (id, cycle_id, sequence_number, name)
                VALUES (?, ?, 1, 'Legacy microcycle')
                """, microcycleId, cycleId);
        jdbc.update("""
                INSERT INTO training_planning.planned_session
                    (id, microcycle_id, participant_account_id, title, session_kind, status, assigned_at)
                VALUES (?, ?, ?, 'Legacy appointment', 'OFFLINE_APPOINTMENT', 'ASSIGNED', now())
                """, sessionId, microcycleId, participantId);
        return sessionId;
    }

    private static UUID insertLegacyExerciseVersion(JdbcTemplate jdbc) {
        UUID exerciseId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise
                    (id, canonical_name, created_at, created_by_subject)
                VALUES (?, 'Legacy squat', now(), 'legacy-editor')
                """, exerciseId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version
                    (id, exercise_id, version_number, status, instruction, movement_pattern,
                     stimulus_type, fatigue_profile, technical_level, environment, created_at, version)
                VALUES (?, ?, 1, 'DRAFT', 'Legacy instruction', 'SQUAT', 'STRENGTH',
                        'MODERATE', 'FOUNDATIONAL', 'ANY', now(), 0)
                """, versionId, exerciseId);
        jdbc.update("""
                INSERT INTO exercise_catalog.exercise_version_contraindication
                    (exercise_version_id, contraindication_tag)
                VALUES (?, 'LEGACY_KNEE_TAG')
                """, versionId);
        return versionId;
    }
}
