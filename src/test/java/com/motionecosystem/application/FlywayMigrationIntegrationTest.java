package com.motionecosystem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class FlywayMigrationIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appliesFoundationMigration() {
        Integer applied = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = TRUE
                  AND script = 'V001__create_identity_access_and_audit_foundation.sql'
                """, Integer.class);
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE (table_schema, table_name) IN (
                    ('identity_access', 'principal_account'),
                    ('audit', 'audit_event')
                )
                """, Integer.class);

        assertThat(applied).isEqualTo(1);
        assertThat(tables).isEqualTo(2);
    }

    @Test
    void createsIndexedAnatomyReferenceSchemaWithControlledStarterTaxonomySeed() {
        Integer applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success = TRUE AND script = 'V007__create_anatomy_reference.sql'
                """, Integer.class);
        Integer starterTaxonomySeedApplied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success = TRUE AND script = 'V030__seed_starter_anatomy_reference_v1.sql'
                """, Integer.class);
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'anatomy_reference'
                  AND table_name IN ('anatomical_structure', 'anatomical_structure_relation', 'hierarchy_guard')
                """, Integer.class);
        Integer taxonomyRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM anatomy_reference.anatomical_structure", Integer.class);
        Integer controlledTaxonomyRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM anatomy_reference.anatomical_structure
                WHERE status = 'PUBLISHED'
                  AND taxonomy_version = 1
                  AND created_by_subject = 'moves-starter-v1'
                """, Integer.class);
        Integer guardRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM anatomy_reference.hierarchy_guard", Integer.class);

        assertThat(applied).isEqualTo(1);
        assertThat(starterTaxonomySeedApplied).isEqualTo(1);
        assertThat(tables).isEqualTo(3);
        assertThat(taxonomyRows).isEqualTo(62);
        assertThat(controlledTaxonomyRows).isEqualTo(62);
        assertThat(guardRows).isEqualTo(1);
    }

    @Test
    void upgradesExerciseCatalogToVersionedExposureProfilesWithoutMappingLegacyTags() {
        Integer applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success = TRUE AND script = 'V008__create_exercise_catalog_v2.sql'
                """, Integer.class);
        Integer profileTables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'exercise_catalog'
                  AND table_name IN (
                      'exercise_version_movement_pattern', 'exercise_load_characteristic',
                      'evidence_source', 'exercise_contribution', 'exercise_contribution_evidence'
                  )
                """, Integer.class);
        Integer legacyRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exercise_catalog.exercise_version_contraindication", Integer.class);

        assertThat(applied).isEqualTo(1);
        assertThat(profileTables).isEqualTo(5);
        assertThat(legacyRows).isZero();
    }

    @Test
    void createsScopedPlanCollaborationAndOpenReviewConstraint() {
        Integer applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success = TRUE
                  AND script = 'V015__create_plan_collaboration_and_review.sql'
                """, Integer.class);
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'training_planning'
                  AND table_name IN (
                      'plan_collaborator', 'plan_collaborator_scope', 'plan_review_request'
                  )
                """, Integer.class);
        String openReviewPredicate = jdbc.queryForObject("""
                SELECT pg_get_expr(index.indpred, index.indrelid)
                FROM pg_index index
                JOIN pg_class relation ON relation.oid = index.indexrelid
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'training_planning'
                  AND relation.relname = 'uq_open_plan_review'
                """, String.class);

        assertThat(applied).isEqualTo(1);
        assertThat(tables).isEqualTo(3);
        assertThat(openReviewPredicate).contains("status", "OPEN");
    }

    @Test
    @Transactional
    void keepsLegacyOfflineAppointmentsButRejectsNewPlanningAppointments() {
        UUID microcycleId = planningHierarchy();
        UUID legacySessionId = UUID.randomUUID();
        UUID participantId = participantIdFor(microcycleId);

        jdbc.update("""
                INSERT INTO training_planning.planned_session
                    (id, microcycle_id, participant_id, title, session_kind, status,
                     assigned_at, creation_source)
                VALUES (?, ?, ?, 'Legacy appointment', 'OFFLINE_APPOINTMENT', 'ASSIGNED',
                        now(), 'LEGACY_V1')
                """, legacySessionId, microcycleId, participantId);

        assertThat(jdbc.queryForObject("""
                SELECT creation_source FROM training_planning.planned_session WHERE id = ?
                """, String.class, legacySessionId)).isEqualTo("LEGACY_V1");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO training_planning.planned_session
                    (id, microcycle_id, participant_id, title, session_kind, status, assigned_at)
                VALUES (?, ?, ?, 'New appointment', 'OFFLINE_APPOINTMENT', 'ASSIGNED', now())
                """, UUID.randomUUID(), microcycleId, participantId))
                .hasMessageContaining("ck_offline_appointment_legacy_only");
    }

    @Test
    @Transactional
    void databaseAllowsOnlyOneSuccessfulExecutionPerPlannedSession() {
        UUID sessionId = selfGuidedSession();

        insertExecution(sessionId, "first-execution");

        assertThatThrownBy(() -> insertExecution(sessionId, "second-execution"))
                .hasMessageContaining("uq_session_execution_successful_session");
    }

    private UUID planningHierarchy() {
        UUID participantId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID microcycleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_access.principal_account
                    (id, external_subject, status, profile_type, created_at, version)
                VALUES (?, ?, 'ACTIVE', 'SPECIALIST', now(), 0)
                """, authorId, "migration-author-" + authorId);
        jdbc.update("""
                INSERT INTO participant.participant_record
                    (id, display_name, record_status, relationship_context, created_by_specialist_id, created_at, updated_at, version)
                VALUES (?, 'Migration participant', 'ACTIVE', 'CLIENT', ?, now(), now(), 0)
                """, participantId, authorId);
        jdbc.update("""
                INSERT INTO training_planning.training_goal
                    (id, participant_id, name, created_by_account_id, created_at)
                VALUES (?, ?, 'Migration goal', ?, now())
                """, goalId, participantId, authorId);
        jdbc.update("""
                INSERT INTO training_planning.training_plan
                    (id, goal_id, participant_id, created_by_account_id, name,
                     plan_mode, status, created_at, purpose, owner_account_id)
                VALUES (?, ?, ?, ?, 'Migration plan', 'SPECIALIST_ASSIGNED', 'ACTIVE', now(),
                        'Legacy migration fixture', ?)
                """, planId, goalId, participantId, authorId, authorId);
        jdbc.update("""
                INSERT INTO training_planning.training_cycle (id, plan_id, sequence_number, name)
                VALUES (?, ?, 1, 'Migration cycle')
                """, cycleId, planId);
        jdbc.update("""
                INSERT INTO training_planning.microcycle (id, cycle_id, sequence_number, name)
                VALUES (?, ?, 1, 'Migration microcycle')
                """, microcycleId, cycleId);
        return microcycleId;
    }

    private UUID selfGuidedSession() {
        UUID sessionId = UUID.randomUUID();
        UUID microcycleId = planningHierarchy();
        jdbc.update("""
                INSERT INTO training_planning.planned_session
                    (id, microcycle_id, participant_id, title, session_kind, status, assigned_at)
                VALUES (?, ?, ?, 'Self guided', 'SELF_GUIDED', 'ASSIGNED', now())
                """, sessionId, microcycleId, participantIdFor(microcycleId));
        return sessionId;
    }

    private void insertExecution(UUID sessionId, String key) {
        jdbc.update("""
                INSERT INTO training_execution.session_execution
                    (id, planned_session_id, participant_id, declared_completion,
                     idempotency_key, recorded_at)
                VALUES (?, ?, ?, TRUE, ?, now())
                """, UUID.randomUUID(), sessionId, participantIdForSession(sessionId), key);
    }

    private UUID participantIdFor(UUID microcycleId) {
        return jdbc.queryForObject("""
                SELECT plan.participant_id
                FROM training_planning.microcycle microcycle
                JOIN training_planning.training_cycle cycle ON cycle.id = microcycle.cycle_id
                JOIN training_planning.training_plan plan ON plan.id = cycle.plan_id
                WHERE microcycle.id = ?
                """, UUID.class, microcycleId);
    }

    private UUID participantIdForSession(UUID sessionId) {
        return jdbc.queryForObject("SELECT participant_id FROM training_planning.planned_session WHERE id = ?",
                UUID.class, sessionId);
    }
}
