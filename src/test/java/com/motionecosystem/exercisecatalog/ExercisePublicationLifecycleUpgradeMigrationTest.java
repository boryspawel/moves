package com.motionecosystem.exercisecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class ExercisePublicationLifecycleUpgradeMigrationTest {
    @Test
    void upgradesV061PreservingHistoryAndAllowsNewUnreviewedPublicationOnly() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("061").load().migrate();
            try (JpaFixture fixture = JpaFixture.open(postgres)) {
                Ids ids = fixture.seedV061();
                Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .load().migrate();

                assertThat(fixture.one("select status from exercise_catalog.exercise_version where id=:id", ids.published()))
                        .isEqualTo("PUBLISHED");
                assertThat(fixture.one("select reviewed_at from exercise_catalog.exercise_version where id=:id", ids.published()))
                        .isEqualTo(ids.at());
                assertThat(fixture.count("select count(*) from exercise_catalog.exercise_review where exercise_version_id=:id", ids.published())).isEqualTo(4);
                assertThat(fixture.one("select status from exercise_catalog.exercise_version where id=:id", ids.approved()))
                        .isEqualTo("APPROVED");
                assertThat(fixture.one("select reviewed_at from exercise_catalog.exercise_version where id=:id", ids.approved()))
                        .isEqualTo(ids.at());
                assertThat(fixture.count("select count(*) from exercise_catalog.exercise_contribution_evidence where id=:id", ids.publishedLink())).isOne();
                assertThatThrownBy(() -> fixture.tx(() -> fixture.update("delete from exercise_catalog.exercise_contribution_evidence where id=:id", "id", ids.publishedLink())))
                        .isInstanceOf(RuntimeException.class);
                assertThatThrownBy(() -> fixture.tx(() -> fixture.update("insert into exercise_catalog.exercise_contribution_evidence(id,contribution_id,evidence_source_id) values(:id,:contribution,:evidence)", "id", UUID.randomUUID(), "contribution", ids.publishedContribution(), "evidence", ids.publishedEvidence())))
                        .isInstanceOf(RuntimeException.class);
                assertThatThrownBy(() -> fixture.tx(() -> fixture.update("update exercise_catalog.exercise_contribution_evidence set contribution_id=:contribution where id=:id", "id", ids.draftLink(), "contribution", ids.publishedContribution())))
                        .isInstanceOf(RuntimeException.class);
                assertThatThrownBy(() -> fixture.tx(() -> fixture.update("update exercise_catalog.exercise_contribution_evidence set contribution_id=:contribution where id=:id", "id", ids.publishedLink(), "contribution", ids.draftContribution())))
                        .isInstanceOf(RuntimeException.class);
                fixture.tx(() -> fixture.update("delete from exercise_catalog.exercise_contribution_evidence where id=:id", "id", ids.draftLink()));
                assertThat(fixture.count("select count(*) from exercise_catalog.exercise_contribution_evidence where id=:id", ids.draftLink())).isZero();
                fixture.tx(() -> fixture.update("update exercise_catalog.exercise_version set status='PUBLISHED', published_at=:at where id=:id", "id", ids.newDraft(), "at", ids.at()));
                assertThat(fixture.one("select reviewed_at from exercise_catalog.exercise_version where id=:id", ids.newDraft())).isNull();
                fixture.tx(() -> fixture.update("delete from exercise_catalog.exercise_version where id=:id", "id", ids.deletable()));
                assertThat(fixture.count("select count(*) from exercise_catalog.exercise_version where id=:id", ids.deletable())).isZero();
                assertThatThrownBy(() -> fixture.tx(() -> fixture.update("delete from exercise_catalog.exercise_version where id=:id", "id", ids.published())))
                        .isInstanceOf(RuntimeException.class);
            }
        }
    }

    private record Ids(UUID published, UUID approved, UUID newDraft, UUID deletable, UUID publishedContribution, UUID draftContribution,
                       UUID publishedEvidence, UUID draftEvidence, UUID publishedLink, UUID draftLink, Instant at) {}

    private static final class JpaFixture implements AutoCloseable {
        final LocalContainerEntityManagerFactoryBean factory; final EntityManagerFactory emf; final EntityManager em;
        private JpaFixture(LocalContainerEntityManagerFactoryBean factory) { this.factory=factory; emf=factory.getObject(); em=emf.createEntityManager(); }
        static JpaFixture open(PostgreSQLContainer<?> postgres) { LocalContainerEntityManagerFactoryBean factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())); factory.setPackagesToScan("com.motionecosystem"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none")); factory.afterPropertiesSet(); return new JpaFixture(factory); }
        Ids seedV061() { Instant at=Instant.parse("2026-09-16T08:00:00Z"); UUID published=version("Historical published", "DRAFT", null, null), approved=version("Historical approved", "DRAFT", null, null), draft=version("New draft", "DRAFT", null, null), deletable=version("Delete draft", "DRAFT", null, null); UUID publishedContribution=contribution(published, at), draftContribution=contribution(draft, at), publishedEvidence=evidence(published, at), draftEvidence=evidence(draft, at), publishedLink=link(publishedContribution,publishedEvidence), draftLink=link(draftContribution,draftEvidence); publish(published, at); approve(approved, at); return new Ids(published,approved,draft,deletable,publishedContribution,draftContribution,publishedEvidence,draftEvidence,publishedLink,draftLink,at); }
        UUID version(String name,String status,Instant publishedAt,Instant reviewedAt) { UUID exercise=UUID.randomUUID(), version=UUID.randomUUID(); Instant now=Instant.parse("2026-09-16T08:00:00Z"); tx(()->{update("insert into exercise_catalog.exercise(id,canonical_name,created_at,created_by_subject) values(:id,:name,:at,'legacy')","id",exercise,"name",name,"at",now); update("insert into exercise_catalog.exercise_version(id,exercise_id,version_number,status,instruction,movement_pattern,stimulus_type,fatigue_profile,technical_level,environment,created_at,profile_schema_version,version,content_revision) values(:id,:exercise,1,'DRAFT','legacy','SQUAT','STRENGTH','MODERATE','FOUNDATIONAL','ANY',:at,2,0,0)","id",version,"exercise",exercise,"at",now); update("insert into exercise_catalog.exercise_version_movement_pattern(exercise_version_id,movement_pattern) values(:id,'SQUAT')","id",version);}); if("PUBLISHED".equals(status))tx(()->{for(String area:java.util.List.of("CONTENT","TECHNIQUE","ANATOMY_EXPOSURE","LICENSE"))update("insert into exercise_catalog.exercise_review(id,exercise_version_id,review_area,decision,reviewer_subject,reviewed_at,content_revision,version) values(:id,:version,:area,'APPROVED','legacy',:at,0,0)","id",UUID.randomUUID(),"version",version,"area",area,"at",reviewedAt); update("update exercise_catalog.exercise_version set status='APPROVED',reviewed_at=:at,reviewed_by_subject='legacy' where id=:id","id",version,"at",reviewedAt);update("update exercise_catalog.exercise_version set status='PUBLISHED',published_at=:at where id=:id","id",version,"at",publishedAt);}); return version; }
        void publish(UUID version, Instant at) { tx(()->{for(String area:java.util.List.of("CONTENT","TECHNIQUE","ANATOMY_EXPOSURE","LICENSE"))update("insert into exercise_catalog.exercise_review(id,exercise_version_id,review_area,decision,reviewer_subject,reviewed_at,content_revision,version) values(:id,:version,:area,'APPROVED','legacy',:at,0,0)","id",UUID.randomUUID(),"version",version,"area",area,"at",at); update("update exercise_catalog.exercise_version set status='APPROVED',reviewed_at=:at,reviewed_by_subject='legacy' where id=:id","id",version,"at",at);update("update exercise_catalog.exercise_version set status='PUBLISHED',published_at=:at where id=:id","id",version,"at",at);}); }
        void approve(UUID version, Instant at) { tx(()->update("update exercise_catalog.exercise_version set status='APPROVED',reviewed_at=:at,reviewed_by_subject='legacy' where id=:id","id",version,"at",at)); }
        UUID contribution(UUID version, Instant at) { UUID id=UUID.randomUUID(); tx(()->update("insert into exercise_catalog.exercise_contribution(id,exercise_version_id,anatomical_structure_id,contribution_role,load_channel,contribution_band,coefficient_low,coefficient_high,confidence_class,evidence_grade,calculation_role,side_rule,created_at,created_by_subject) values(:id,:version,:structure,'PRIMARY','DYN_EXU','HIGH',0.2,0.7,'MODERATE','EDITORIAL_REVIEW','ALLOCATION','AS_PRESCRIBED',:at,'legacy')","id",id,"version",version,"structure",UUID.randomUUID(),"at",at)); return id; }
        UUID evidence(UUID version, Instant at) { UUID id=UUID.randomUUID(); tx(()->update("insert into exercise_catalog.evidence_source(id,exercise_version_id,citation,evidence_grade,created_at,created_by_subject) values(:id,:version,'legacy evidence','EDITORIAL_REVIEW',:at,'legacy')","id",id,"version",version,"at",at)); return id; }
        UUID link(UUID contribution, UUID evidence) { UUID id=UUID.randomUUID(); tx(()->update("insert into exercise_catalog.exercise_contribution_evidence(id,contribution_id,evidence_source_id) values(:id,:contribution,:evidence)","id",id,"contribution",contribution,"evidence",evidence)); return id; }
        void tx(Runnable action){em.getTransaction().begin();try{action.run();em.getTransaction().commit();}catch(RuntimeException failure){if(em.getTransaction().isActive())em.getTransaction().rollback();throw failure;}}
        void update(String sql,Object... bindings){var query=em.createNativeQuery(sql);for(int i=0;i<bindings.length;i+=2)query.setParameter((String)bindings[i],bindings[i+1]);query.executeUpdate();}
        Object one(String sql,UUID id){return em.createNativeQuery(sql).setParameter("id",id).getSingleResult();}
        long count(String sql,UUID id){return ((Number)em.createNativeQuery(sql).setParameter("id",id).getSingleResult()).longValue();}
        public void close(){em.close();emf.close();factory.destroy();}
    }
}
