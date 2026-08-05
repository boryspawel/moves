package com.motionecosystem.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.identityaccess.domain.PrincipalAccount;
import com.motionecosystem.participant.ParticipantRecord;
import com.motionecosystem.support.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class AppointmentEventPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void widensClassificationColumnsAndPersistsCompletedEventThroughJpa() {
        assertThat(classificationColumnLengths()).containsOnly(
                Map.entry("event_type", 64),
                Map.entry("from_status", 64),
                Map.entry("to_status", 64),
                Map.entry("type", 64),
                Map.entry("location_mode", 64));

        PrincipalAccount specialist = PrincipalAccount.create("appointment-event-specialist-" + UUID.randomUUID(), NOW);
        entityManager.persist(specialist);
        ParticipantRecord participant = new ParticipantRecord("Appointment event participant",
                ParticipantRecord.RelationshipContext.CLIENT, null, null, ZoneOffset.UTC, specialist.id(), NOW);
        entityManager.persist(participant);
        Appointment appointment = new Appointment(specialist.id(), participant.id(), NOW, NOW.plusSeconds(3600),
                Appointment.Type.CONSULTATION, Appointment.LocationMode.REMOTE, null, null, specialist.id(), NOW);
        appointment.complete(NOW);
        entityManager.persist(appointment);
        AppointmentEvent event = new AppointmentEvent(appointment, AppointmentEvent.Type.COMPLETED,
                Appointment.Status.SCHEDULED, NOW, NOW, specialist.id(), null, null);
        entityManager.persist(event);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(AppointmentEvent.class, event.id).eventType).isEqualTo(AppointmentEvent.Type.COMPLETED);
    }

    @Test
    @Transactional
    void widensIdempotencyOperationAndPersistsCompleteOperationThroughJpa() {
        assertThat(columnLength("appointment_idempotency", "operation")).isEqualTo(64);

        PrincipalAccount specialist = PrincipalAccount.create("appointment-idempotency-specialist-" + UUID.randomUUID(), NOW);
        entityManager.persist(specialist);
        ParticipantRecord participant = new ParticipantRecord("Appointment idempotency participant",
                ParticipantRecord.RelationshipContext.CLIENT, null, null, ZoneOffset.UTC, specialist.id(), NOW);
        entityManager.persist(participant);
        Appointment appointment = new Appointment(specialist.id(), participant.id(), NOW, NOW.plusSeconds(3600),
                Appointment.Type.CONSULTATION, Appointment.LocationMode.REMOTE, null, null, specialist.id(), NOW);
        entityManager.persist(appointment);
        AppointmentIdempotency idempotency = new AppointmentIdempotency(specialist.id(),
                "COMPLETE:" + appointment.id, "complete-idempotency-key", appointment.id, NOW);
        entityManager.persist(idempotency);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(AppointmentIdempotency.class, idempotency.id).operation)
                .isEqualTo("COMPLETE:" + appointment.id);
    }

    private Map<String, Integer> classificationColumnLengths() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT column_name, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'calendar'
                  AND table_name = 'appointment_event'
                  AND column_name IN ('event_type', 'from_status', 'to_status', 'type', 'location_mode')
                """).getResultList();
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                row -> (String) row[0], row -> ((Number) row[1]).intValue()));
    }

    private int columnLength(String table, String column) {
        return ((Number) entityManager.createNativeQuery("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'calendar'
                  AND table_name = :table
                  AND column_name = :column
                """)
                .setParameter("table", table)
                .setParameter("column", column)
                .getSingleResult()).intValue();
    }
}
