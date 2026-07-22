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
    void upgradesV029WhenStarterAnatomyAndDictionariesAlreadyContainCodes() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:18-alpine"))) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("029")
                    .load()
                    .migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            UUID lowerLimbId = UUID.randomUUID();

            jdbc.update("""
                    INSERT INTO anatomy_reference.anatomical_structure
                        (id, code, type, display_name, side_policy, status, taxonomy_version,
                         created_by_subject, created_at, published_at, version)
                    VALUES (?, 'BODY_REGION:LOWER_LIMB', 'BODY_REGION', 'Existing lower limb',
                            'LEFT_RIGHT', 'PUBLISHED', 7, 'existing-seed', now(), now(), 4)
                    """, lowerLimbId);
            jdbc.update("""
                    INSERT INTO exercise_catalog.exercise_equipment_dictionary
                        (code, display_name, dictionary_version, active)
                    VALUES ('BENCH', 'Existing bench', 7, FALSE)
                    """);
            jdbc.update("""
                    INSERT INTO exercise_catalog.exercise_position_dictionary
                        (code, display_name, dictionary_version, active)
                    VALUES ('FRONT_SUPPORT', 'Existing front support', 7, FALSE)
                    """);

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load()
                    .migrate();

            assertThat(jdbc.queryForMap("""
                    SELECT id, display_name, taxonomy_version, version
                    FROM anatomy_reference.anatomical_structure
                    WHERE code = 'BODY_REGION:LOWER_LIMB'
                    """)).containsEntry("id", lowerLimbId)
                    .containsEntry("display_name", "Existing lower limb")
                    .containsEntry("taxonomy_version", 7)
                    .containsEntry("version", 4L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM anatomy_reference.anatomical_structure_relation relation
                    JOIN anatomy_reference.anatomical_structure parent ON parent.id = relation.parent_id
                    WHERE parent.code = 'BODY_REGION:LOWER_LIMB'
                    """, Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForMap("""
                    SELECT display_name, dictionary_version, active
                    FROM exercise_catalog.exercise_equipment_dictionary WHERE code = 'BENCH'
                    """)).containsEntry("display_name", "Existing bench")
                    .containsEntry("dictionary_version", 7)
                    .containsEntry("active", false);
            assertThat(jdbc.queryForMap("""
                    SELECT display_name, dictionary_version, active
                    FROM exercise_catalog.exercise_position_dictionary WHERE code = 'FRONT_SUPPORT'
                    """)).containsEntry("display_name", "Existing front support")
                    .containsEntry("dictionary_version", 7)
                    .containsEntry("active", false);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM exercise_catalog.exercise_equipment_dictionary WHERE code = 'WALL'
                    """, Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM exercise_catalog.exercise_position_dictionary WHERE code = 'SQUAT'
                    """, Integer.class)).isEqualTo(1);
        }
    }

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
            UUID participantAccountId = insertLegacyAccount(jdbc, "legacy-participant", "PARTICIPANT");
            UUID specialistAccountId = insertLegacyAccount(jdbc, "legacy-specialist", "SPECIALIST");
            insertLegacySpecialistProfile(jdbc, specialistAccountId);
            insertLegacyParticipantRestriction(jdbc, participantAccountId);

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
            assertThat(jdbc.queryForMap("""
                    SELECT revision.revision_number, revision.migration_origin,
                           revision.assessment_status, revision.status
                    FROM training_planning.planned_session session
                    JOIN training_planning.microcycle microcycle ON microcycle.id = session.microcycle_id
                    JOIN training_planning.training_cycle cycle ON cycle.id = microcycle.cycle_id
                    JOIN training_planning.plan_revision revision ON revision.id = cycle.revision_id
                    WHERE session.id = ?
                    """, sessionId)).containsEntry("revision_number", 1)
                    .containsEntry("migration_origin", "LEGACY_V1")
                    .containsEntry("assessment_status", "NOT_ASSESSED")
                    .containsEntry("status", "ACTIVE");
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM identity_access.account_domain_profile
                    WHERE account_id = ? AND profile_type = 'PARTICIPANT'
                    """, String.class, participantAccountId)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM identity_access.account_domain_profile
                    WHERE account_id = ? AND profile_type = 'SPECIALIST'
                    """, String.class, specialistAccountId)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("""
                    SELECT verification_status FROM specialist.professional_scope
                    WHERE specialist_account_id = ? AND scope_type = 'TRAINER'
                    """, String.class, specialistAccountId)).isEqualTo("VERIFIED");
            assertThat(jdbc.queryForObject("""
                    SELECT contraindication_tag FROM safety.participant_restriction
                    WHERE account_id = ?
                    """, String.class, participantAccountId)).isEqualTo("LEGACY_KNEE_TAG");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM safety.restriction WHERE participant_account_id = ?
                    """, Integer.class, participantAccountId)).isZero();
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

    private static UUID insertLegacyAccount(JdbcTemplate jdbc, String subject, String profileType) {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_access.principal_account
                    (id, external_subject, status, profile_type, created_at, version)
                VALUES (?, ?, 'ACTIVE', ?, now(), 0)
                """, accountId, subject, profileType);
        return accountId;
    }

    private static void insertLegacySpecialistProfile(JdbcTemplate jdbc, UUID accountId) {
        jdbc.update("""
                INSERT INTO specialist.specialist_profile
                    (id, account_id, display_name, specialist_kind, created_at, updated_at, version)
                VALUES (?, ?, 'Legacy trainer', 'TRAINER', now(), now(), 0)
                """, UUID.randomUUID(), accountId);
    }

    private static void insertLegacyParticipantRestriction(JdbcTemplate jdbc, UUID accountId) {
        jdbc.update("""
                INSERT INTO safety.participant_restriction
                    (id, account_id, contraindication_tag, recorded_at)
                VALUES (?, ?, 'LEGACY_KNEE_TAG', now())
                """, UUID.randomUUID(), accountId);
    }
}
