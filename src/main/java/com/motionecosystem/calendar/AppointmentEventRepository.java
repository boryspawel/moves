package com.motionecosystem.calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Append-only store contract: calendar code can persist and read history, never mutate or delete it. */
interface AppointmentEventRepository extends Repository<AppointmentEvent, UUID> {
    <S extends AppointmentEvent> S save(S event);
    @Query("""
            select e.id as eventId, e.appointmentId as appointmentId, cast(e.eventType as string) as eventType,
                   cast(e.fromStatus as string) as fromStatus, cast(e.toStatus as string) as toStatus, e.effectiveAt as effectiveAt,
                   e.recordedAt as recordedAt, cast(e.type as string) as appointmentType, e.shortPurpose as shortPurpose
            from AppointmentEvent e where e.specialistAccountId = :specialist and e.participantId = :participant
              and e.effectiveAt >= :from and e.effectiveAt < :to
            order by e.effectiveAt desc, e.recordedAt desc, e.id asc
            """)
    List<AppointmentEventProjection> findInitial(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select e.id as eventId, e.appointmentId as appointmentId, cast(e.eventType as string) as eventType,
                   cast(e.fromStatus as string) as fromStatus, cast(e.toStatus as string) as toStatus, e.effectiveAt as effectiveAt,
                   e.recordedAt as recordedAt, cast(e.type as string) as appointmentType, e.shortPurpose as shortPurpose
            from AppointmentEvent e where e.specialistAccountId = :specialist and e.participantId = :participant
              and e.effectiveAt >= :from and e.effectiveAt < :to and
              (e.effectiveAt < :effective or (e.effectiveAt = :effective and
                (e.recordedAt < :recorded or (e.recordedAt = :recorded and e.id > :id))))
            order by e.effectiveAt desc, e.recordedAt desc, e.id asc
            """)
    List<AppointmentEventProjection> findAfter(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, @Param("effective") Instant effective,
            @Param("recorded") Instant recorded, @Param("id") UUID id, Pageable pageable);

    @Query("""
            select e.id as eventId, e.appointmentId as appointmentId, cast(e.eventType as string) as eventType,
                   cast(e.fromStatus as string) as fromStatus, cast(e.toStatus as string) as toStatus, e.effectiveAt as effectiveAt,
                   e.recordedAt as recordedAt, cast(e.type as string) as appointmentType, e.shortPurpose as shortPurpose
            from AppointmentEvent e where e.id = :eventId and e.specialistAccountId = :specialist and e.participantId = :participant
            """)
    java.util.Optional<AppointmentEventProjection> findBySpecialistAndParticipant(@Param("specialist") UUID specialist,
            @Param("participant") UUID participant, @Param("eventId") UUID eventId);

    @Query("select e.id from AppointmentEvent e where e.appointmentId = :appointmentId order by e.recordedAt desc, e.id desc")
    List<UUID> findLatestEventId(@Param("appointmentId") UUID appointmentId, Pageable pageable);

    interface AppointmentEventProjection {
        UUID getEventId();
        UUID getAppointmentId();
        String getEventType();
        String getFromStatus();
        String getToStatus();
        Instant getEffectiveAt();
        Instant getRecordedAt();
        String getAppointmentType();
        String getShortPurpose();
    }
}
