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
}
