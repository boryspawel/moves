package com.motionecosystem.trainingexecution;

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

class AttemptExecutionFactsUpgradeMigrationTest {
    @Test
    void upgradesV058WithoutInventingExecutionFactHistory() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target("058").load().migrate();
            try (JpaFixture fixture = JpaFixture.open(postgres)) {
                Ids ids = fixture.seedV058();
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
                fixture.entityManager.clear();
                Object[] execution = (Object[]) fixture.query("SELECT id, recorded_at, outcome, attempt_id FROM training_execution.session_execution WHERE id=:id", ids.executionId());
                assertThat(execution[0]).isEqualTo(ids.executionId());
                assertThat(execution[1]).isEqualTo(ids.recordedAt());
                assertThat(execution[2]).isEqualTo("LEGACY");
                assertThat(execution[3]).isNull();
                assertThat(fixture.query("SELECT COUNT(*) FROM training_execution.session_execution_attempt_fact WHERE attempt_id=:id", ids.attemptId())).isEqualTo(0L);
                assertThat(fixture.query("SELECT actual_repetitions FROM training_execution.exercise_result WHERE id=:id", ids.resultId())).isEqualTo(8);
                assertThat(fixture.query("SELECT corrected_repetitions FROM training_execution.execution_correction WHERE id=:id", ids.correctionId())).isEqualTo(7);
                assertThat(fixture.query("SELECT participant_account_id FROM analytics.adherence_metric_event WHERE id=:id", ids.linkedMetricId())).isEqualTo(ids.accountId());
                assertThat(fixture.query("SELECT participant_id FROM analytics.adherence_metric_event WHERE id=:id", ids.linkedMetricId())).isEqualTo(ids.participantId());
                assertThat(fixture.query("SELECT participant_id FROM analytics.adherence_metric_event WHERE id=:id", ids.unmappedMetricId())).isNull();
                fixture.tx(() -> fixture.update("INSERT INTO training_execution.session_execution (id, planned_session_id, participant_id, declared_completion, idempotency_key, recorded_at, projection_status, outcome) VALUES (:id,:session,:participant,false,'partial-new',now(),'PENDING','PARTIAL')", "id", UUID.randomUUID(), "session", UUID.randomUUID(), "participant", ids.participantId()));
            }
        }
    }

    private record Ids(UUID accountId, UUID participantId, UUID executionId, UUID resultId, UUID correctionId, UUID attemptId, UUID linkedMetricId, UUID unmappedMetricId, Instant recordedAt) { }

    private static final class JpaFixture implements AutoCloseable {
        final LocalContainerEntityManagerFactoryBean factory; final EntityManagerFactory emf; final EntityManager entityManager;
        private JpaFixture(LocalContainerEntityManagerFactoryBean factory) { this.factory=factory; this.emf=factory.getObject(); this.entityManager=emf.createEntityManager(); }
        static JpaFixture open(PostgreSQLContainer<?> postgres) { LocalContainerEntityManagerFactoryBean f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())); f.setPackagesToScan("com.motionecosystem"); f.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none")); f.afterPropertiesSet(); return new JpaFixture(f); }
        Ids seedV058() { UUID account=UUID.randomUUID(), participant=UUID.randomUUID(), execution=UUID.randomUUID(), result=UUID.randomUUID(), correction=UUID.randomUUID(), attempt=UUID.randomUUID(), linked=UUID.randomUUID(), unmapped=UUID.randomUUID(); Instant recorded=Instant.parse("2026-09-14T10:00:00Z"); tx(() -> { update("INSERT INTO identity_access.principal_account(id,external_subject,status,profile_type,created_at,version) VALUES(:id,'upgrade-linked','ACTIVE','PARTICIPANT',:at,0)","id",account,"at",recorded); update("INSERT INTO participant.participant_record(id,display_name,record_status,relationship_context,created_by_specialist_id,created_at,updated_at,version) VALUES(:id,'upgrade','ACTIVE','CLIENT',:creator,:at,:at,0)","id",participant,"creator",account,"at",recorded); update("INSERT INTO participant.participant_access_link(id,participant_id,principal_account_id,access_status,linked_at,activated_at,version) VALUES(:id,:participant,:account,'ACTIVE',:at,:at,0)","id",UUID.randomUUID(),"participant",participant,"account",account,"at",recorded); update("INSERT INTO training_execution.session_execution(id,planned_session_id,participant_id,declared_completion,idempotency_key,recorded_at,projection_status) VALUES(:id,:session,:participant,true,'legacy-exec',:at,'PENDING')","id",execution,"session",UUID.randomUUID(),"participant",participant,"at",recorded); update("INSERT INTO training_execution.exercise_result(id,session_execution_id,exercise_prescription_id,actual_repetitions,modified,skipped,observation_mode) VALUES(:id,:execution,:prescription,8,false,false,'DECLARED')","id",result,"execution",execution,"prescription",UUID.randomUUID()); update("INSERT INTO training_execution.execution_correction(id,session_execution_id,corrected_by_account_id,reason,corrected_repetitions,corrected_at,idempotency_key,observation_mode) VALUES(:id,:execution,:account,'legacy',7,:at,'legacy-correction','DECLARED')","id",correction,"execution",execution,"account",account,"at",recorded); update("INSERT INTO training_execution.session_execution_attempt(id,participant_id,planned_session_id,plan_revision_id,status,started_at,updated_at,selected_variant_type,last_activity_at,start_idempotency_key,version) VALUES(:id,:participant,:session,:revision,'COMPLETED',:at,:at,'STANDARD',:at,'legacy',0)","id",attempt,"participant",participant,"session",UUID.randomUUID(),"revision",UUID.randomUUID(),"at",recorded); update("INSERT INTO analytics.adherence_metric_event(id,participant_account_id,event_code,technical_reference_id,deduplication_key,occurred_at,expires_at) VALUES(:id,:account,'SESSION_COMPLETED',:reference,'linked-metric',:at,:expiry)","id",linked,"account",account,"reference",execution,"at",recorded,"expiry",recorded.plusSeconds(60)); UUID legacy=UUID.randomUUID(); update("INSERT INTO identity_access.principal_account(id,external_subject,status,profile_type,created_at,version) VALUES(:id,'upgrade-unmapped','ACTIVE','PARTICIPANT',:at,0)","id",legacy,"at",recorded); update("INSERT INTO analytics.adherence_metric_event(id,participant_account_id,event_code,technical_reference_id,deduplication_key,occurred_at,expires_at) VALUES(:id,:account,'SESSION_COMPLETED',:reference,'unmapped-metric',:at,:expiry)","id",unmapped,"account",legacy,"reference",attempt,"at",recorded,"expiry",recorded.plusSeconds(60)); }); return new Ids(account,participant,execution,result,correction,attempt,linked,unmapped,recorded); }
        void tx(Runnable work){entityManager.getTransaction().begin();work.run();entityManager.getTransaction().commit();}
        void update(String sql,Object... p){var q=entityManager.createNativeQuery(sql);for(int i=0;i<p.length;i+=2)q.setParameter((String)p[i],p[i+1]);q.executeUpdate();}
        Object query(String sql,UUID id){return entityManager.createNativeQuery(sql).setParameter("id",id).getSingleResult();}
        public void close(){entityManager.close();emf.close();factory.destroy();}
    }
}
