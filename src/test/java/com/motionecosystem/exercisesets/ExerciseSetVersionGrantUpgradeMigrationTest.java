package com.motionecosystem.exercisesets;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ExerciseSetVersionGrantUpgradeMigrationTest {

    @Test
    void upgradesV059WithoutChangingOwnedSetMetadataOrCreatingGrants() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("059").load().migrate();
            try (JpaFixture fixture = JpaFixture.open(postgres)) {
                Ids ids = fixture.seedV059();
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .load().migrate();

                assertThat(fixture.query("""
                        SELECT id, owner_account_id, visibility, version FROM exercise_set.exercise_set WHERE id = :id
                        """, ids.setId())).containsExactly(ids.setId(), ids.ownerId(), "SHARED", 4L);
                assertThat(fixture.query("""
                        SELECT id, exercise_set_id, status, profile, title, description, target_level, tags, version
                        FROM exercise_set.exercise_set_version WHERE id = :id
                        """, ids.versionId())).containsExactly(ids.versionId(), ids.setId(), "DRAFT", "MAIN_MODULE",
                        "Owned draft", "Existing metadata", "FOUNDATIONAL", "[\"strength\"]", 7L);
                assertThat(fixture.count("SELECT COUNT(*) FROM exercise_set.exercise_set_version_grant")).isZero();
            }
        }
    }

    private record Ids(UUID ownerId, UUID setId, UUID versionId) { }

    private static final class JpaFixture implements AutoCloseable {
        private final LocalContainerEntityManagerFactoryBean factory;
        private final EntityManagerFactory entityManagerFactory;
        private final EntityManager entityManager;

        private JpaFixture(LocalContainerEntityManagerFactoryBean factory) {
            this.factory = factory;
            this.entityManagerFactory = factory.getObject();
            this.entityManager = entityManagerFactory.createEntityManager();
        }

        static JpaFixture open(PostgreSQLContainer<?> postgres) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            factory.setPackagesToScan("com.motionecosystem");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            factory.afterPropertiesSet();
            return new JpaFixture(factory);
        }

        Ids seedV059() {
            UUID ownerId = UUID.randomUUID();
            UUID setId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-09-14T10:00:00Z");
            tx(() -> {
                update("""
                        INSERT INTO identity_access.principal_account(id, external_subject, status, profile_type, created_at, version)
                        VALUES (:id, 'grant-upgrade-owner', 'ACTIVE', 'SPECIALIST', :createdAt, 0)
                        """, "id", ownerId, "createdAt", createdAt);
                update("""
                        INSERT INTO exercise_set.exercise_set(id, owner_account_id, visibility, created_at, updated_at, version)
                        VALUES (:id, :ownerId, 'SHARED', :createdAt, :createdAt, 4)
                        """, "id", setId, "ownerId", ownerId, "createdAt", createdAt);
                update("""
                        INSERT INTO exercise_set.exercise_set_version
                            (id, exercise_set_id, version_number, status, profile, title, description, target_level, tags,
                             variant_kind, author_account_id, created_at, updated_at, version)
                        VALUES (:id, :setId, 1, 'DRAFT', 'MAIN_MODULE', 'Owned draft', 'Existing metadata', 'FOUNDATIONAL',
                                CAST('[\"strength\"]' AS jsonb), 'BASE', :ownerId, :createdAt, :createdAt, 7)
                        """, "id", versionId, "setId", setId, "ownerId", ownerId, "createdAt", createdAt);
            });
            return new Ids(ownerId, setId, versionId);
        }

        private void tx(Runnable work) {
            entityManager.getTransaction().begin();
            work.run();
            entityManager.getTransaction().commit();
        }

        private void update(String sql, Object... bindings) {
            var query = entityManager.createNativeQuery(sql);
            for (int index = 0; index < bindings.length; index += 2) {
                query.setParameter((String) bindings[index], bindings[index + 1]);
            }
            query.executeUpdate();
        }

        private Object[] query(String sql, UUID id) {
            return (Object[]) entityManager.createNativeQuery(sql).setParameter("id", id).getSingleResult();
        }

        private long count(String sql) {
            return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
        }

        @Override
        public void close() {
            entityManager.close();
            entityManagerFactory.close();
            factory.destroy();
        }
    }
}
