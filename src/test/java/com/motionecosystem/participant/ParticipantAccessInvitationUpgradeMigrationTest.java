package com.motionecosystem.participant;

import static org.assertj.core.api.Assertions.assertThat;

import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.domain.PrincipalAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ParticipantAccessInvitationUpgradeMigrationTest {

    @Test
    void upgradesV057WithoutChangingExistingActiveOrSuspendedParticipantLinks() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("057").load().migrate();

            try (JpaFixture fixture = JpaFixture.open(postgres)) {
                FixtureIds ids = fixture.seed();

                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .load().migrate();

                fixture.entityManager.clear();
                ParticipantRecord activeRecord = fixture.entityManager.find(ParticipantRecord.class, ids.activeParticipantId());
                ParticipantRecord suspendedRecord = fixture.entityManager.find(ParticipantRecord.class, ids.suspendedParticipantId());
                ParticipantAccessLink activeLink = fixture.entityManager.createQuery(
                                "select link from ParticipantAccessLink link where link.participantId = :participantId", ParticipantAccessLink.class)
                        .setParameter("participantId", ids.activeParticipantId()).getSingleResult();
                ParticipantAccessLink suspendedLink = fixture.entityManager.createQuery(
                                "select link from ParticipantAccessLink link where link.participantId = :participantId", ParticipantAccessLink.class)
                        .setParameter("participantId", ids.suspendedParticipantId()).getSingleResult();

                assertThat(activeRecord.id()).isEqualTo(ids.activeParticipantId());
                assertThat(suspendedRecord.id()).isEqualTo(ids.suspendedParticipantId());
                assertThat(instantValue(activeRecord, "createdAt")).isEqualTo(ids.activeRecordCreatedAt());
                assertThat(instantValue(suspendedRecord, "createdAt")).isEqualTo(ids.suspendedRecordCreatedAt());
                assertThat(activeRecord.version()).isEqualTo(ids.activeRecordVersion());
                assertThat(suspendedRecord.version()).isEqualTo(ids.suspendedRecordVersion());
                assertThat(activeRecord.recordStatus()).isEqualTo(ParticipantRecord.Status.ACTIVE);
                assertThat(suspendedRecord.recordStatus()).isEqualTo(ParticipantRecord.Status.ACTIVE);
                assertThat(uuidValue(activeLink, "id")).isEqualTo(ids.activeLinkId());
                assertThat(uuidValue(suspendedLink, "id")).isEqualTo(ids.suspendedLinkId());
                assertThat(instantValue(activeLink, "linkedAt")).isEqualTo(ids.activeLinkLinkedAt());
                assertThat(instantValue(suspendedLink, "linkedAt")).isEqualTo(ids.suspendedLinkLinkedAt());
                assertThat(activeLink.principalAccountId()).isEqualTo(ids.activeAccountId());
                assertThat(activeLink.accessStatus()).isEqualTo(ParticipantAccessLink.Status.ACTIVE);
                assertThat(suspendedLink.principalAccountId()).isEqualTo(ids.suspendedAccountId());
                assertThat(suspendedLink.accessStatus()).isEqualTo(ParticipantAccessLink.Status.SUSPENDED);
                assertThat(fixture.entityManager.createQuery("select count(invitation) from ParticipantAccessInvitation invitation", Long.class)
                        .getSingleResult()).isZero();
                assertThat(fixture.entityManager.createQuery("select count(context) from ParticipantClaimContext context", Long.class)
                        .getSingleResult()).isZero();
            }
        }
    }

    private record FixtureIds(UUID activeParticipantId, UUID suspendedParticipantId, UUID activeAccountId, UUID suspendedAccountId,
                              Instant activeRecordCreatedAt, Instant suspendedRecordCreatedAt, long activeRecordVersion, long suspendedRecordVersion,
                              UUID activeLinkId, UUID suspendedLinkId, Instant activeLinkLinkedAt, Instant suspendedLinkLinkedAt) { }

    private static UUID uuidValue(Object target, String field) { return (UUID) org.springframework.test.util.ReflectionTestUtils.getField(target, field); }
    private static Instant instantValue(Object target, String field) { return (Instant) org.springframework.test.util.ReflectionTestUtils.getField(target, field); }

    private static final class JpaFixture implements AutoCloseable {
        private final LocalContainerEntityManagerFactoryBean factory;
        private final EntityManagerFactory entityManagerFactory;
        private final EntityManager entityManager;

        private JpaFixture(LocalContainerEntityManagerFactoryBean factory, EntityManagerFactory entityManagerFactory) {
            this.factory = factory;
            this.entityManagerFactory = entityManagerFactory;
            this.entityManager = entityManagerFactory.createEntityManager();
        }

        static JpaFixture open(PostgreSQLContainer<?> postgres) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            factory.setPackagesToScan("com.motionecosystem");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            factory.afterPropertiesSet();
            return new JpaFixture(factory, factory.getObject());
        }

        FixtureIds seed() {
            entityManager.getTransaction().begin();
            Instant now = Instant.parse("2026-09-14T10:00:00Z");
            PrincipalAccount specialist = account("upgrade-specialist", ProfileType.SPECIALIST, now);
            PrincipalAccount activeAccount = account("upgrade-active", ProfileType.PARTICIPANT, now);
            PrincipalAccount suspendedAccount = account("upgrade-suspended", ProfileType.PARTICIPANT, now);
            entityManager.persist(specialist);
            entityManager.persist(activeAccount);
            entityManager.persist(suspendedAccount);
            ParticipantRecord activeRecord = new ParticipantRecord("Active participant", ParticipantRecord.RelationshipContext.CLIENT,
                    "active@example.test", null, ZoneOffset.UTC, specialist.id(), now);
            ParticipantRecord suspendedRecord = new ParticipantRecord("Suspended participant", ParticipantRecord.RelationshipContext.CLIENT,
                    "suspended@example.test", null, ZoneOffset.UTC, specialist.id(), now);
            entityManager.persist(activeRecord);
            entityManager.persist(suspendedRecord);
            ParticipantAccessLink activeLink = new ParticipantAccessLink(activeRecord.id(), activeAccount.id());
            ParticipantAccessLink suspendedLink = new ParticipantAccessLink(suspendedRecord.id(), suspendedAccount.id());
            org.springframework.test.util.ReflectionTestUtils.setField(suspendedLink, "accessStatus", ParticipantAccessLink.Status.SUSPENDED);
            entityManager.persist(activeLink);
            entityManager.persist(suspendedLink);
            entityManager.getTransaction().commit();
            entityManager.clear();
            ParticipantRecord persistedActiveRecord = entityManager.find(ParticipantRecord.class, activeRecord.id());
            ParticipantRecord persistedSuspendedRecord = entityManager.find(ParticipantRecord.class, suspendedRecord.id());
            ParticipantAccessLink persistedActiveLink = entityManager.createQuery(
                            "select link from ParticipantAccessLink link where link.participantId = :participantId", ParticipantAccessLink.class)
                    .setParameter("participantId", activeRecord.id()).getSingleResult();
            ParticipantAccessLink persistedSuspendedLink = entityManager.createQuery(
                            "select link from ParticipantAccessLink link where link.participantId = :participantId", ParticipantAccessLink.class)
                    .setParameter("participantId", suspendedRecord.id()).getSingleResult();
            return new FixtureIds(persistedActiveRecord.id(), persistedSuspendedRecord.id(), activeAccount.id(), suspendedAccount.id(),
                    instantValue(persistedActiveRecord, "createdAt"), instantValue(persistedSuspendedRecord, "createdAt"), persistedActiveRecord.version(), persistedSuspendedRecord.version(),
                    uuidValue(persistedActiveLink, "id"), uuidValue(persistedSuspendedLink, "id"), instantValue(persistedActiveLink, "linkedAt"), instantValue(persistedSuspendedLink, "linkedAt"));
        }

        private static PrincipalAccount account(String subject, ProfileType type, Instant now) {
            PrincipalAccount account = PrincipalAccount.create(subject, now);
            account.selectProfileType(type);
            return account;
        }

        @Override public void close() {
            entityManager.close();
            entityManagerFactory.close();
            factory.destroy();
        }
    }
}
