package com.motionecosystem.analytics.adherencemetrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class AdherenceMetricsMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsPrivateAppendOnlyMetricAndAssignmentTablesWithExpiryIndex() {
        Integer migration = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success = TRUE AND script = 'V032__create_adherence_metrics.sql'
                """, Integer.class);
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'analytics'
                  AND table_name IN ('adherence_metric_event', 'adherence_experiment_assignment')
                """, Integer.class);
        Integer expiryIndex = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'analytics' AND indexname = 'ix_adherence_metric_event_expiry'
                """, Integer.class);

        assertThat(migration).isEqualTo(1);
        assertThat(tables).isEqualTo(2);
        assertThat(expiryIndex).isEqualTo(1);
    }
}
