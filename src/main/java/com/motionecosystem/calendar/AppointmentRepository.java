package com.motionecosystem.calendar;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @Query("select a from Appointment a where a.specialistAccountId = :specialist and a.startsAt < :end and a.endsAt > :start order by a.startsAt, a.endsAt, a.id")
    List<Appointment> findIntersecting(@Param("specialist") UUID specialist, @Param("start") Instant start, @Param("end") Instant end);
    @Query("""
            select a from Appointment a
            where a.specialistAccountId = :specialist
              and a.participantAccountId = :participant
              and a.startsAt < :to and a.endsAt > :from
            order by a.startsAt desc, a.createdAt desc, a.id asc
            """)
    List<Appointment> findTimelineInitial(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
    @Query("""
            select a from Appointment a
            where a.specialistAccountId = :specialist
              and a.participantAccountId = :participant
              and a.startsAt < :to and a.endsAt > :from
              and (a.startsAt < :effective
                or (a.startsAt = :effective and (a.createdAt < :recorded
                    or (a.createdAt = :recorded and concat('appointment:', cast(a.id as string)) > :eventId))))
            order by a.startsAt desc, a.createdAt desc, a.id asc
            """)
    List<Appointment> findTimelineAfterRecorded(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, @Param("effective") Instant effective,
            @Param("recorded") Instant recorded, @Param("eventId") String eventId, Pageable pageable);
    @Query("""
            select a from Appointment a
            where a.specialistAccountId = :specialist
              and a.participantAccountId = :participant
              and a.startsAt < :to and a.endsAt > :from
              and a.startsAt < :effective
            order by a.startsAt desc, a.createdAt desc, a.id asc
            """)
    List<Appointment> findTimelineAfterUnrecorded(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, @Param("effective") Instant effective, Pageable pageable);
    @Query("select count(a) > 0 from Appointment a where a.specialistAccountId = :specialist and a.status <> 'CANCELLED' and a.id <> :excluded and a.startsAt < :end and a.endsAt > :start")
    boolean hasActiveOverlap(@Param("specialist") UUID specialist, @Param("start") Instant start, @Param("end") Instant end, @Param("excluded") UUID excluded);
}
