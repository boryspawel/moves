package com.motionecosystem.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
