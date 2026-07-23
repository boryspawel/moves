package com.motionecosystem.calendar;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @Query("select a from Appointment a where a.specialistAccountId = :specialist and a.startsAt < :end and a.endsAt > :start order by a.startsAt, a.endsAt, a.id")
    List<Appointment> findIntersecting(@Param("specialist") UUID specialist, @Param("start") Instant start, @Param("end") Instant end);
    @Query("select count(a) > 0 from Appointment a where a.specialistAccountId = :specialist and a.status <> 'CANCELLED' and a.id <> :excluded and a.startsAt < :end and a.endsAt > :start")
    boolean hasActiveOverlap(@Param("specialist") UUID specialist, @Param("start") Instant start, @Param("end") Instant end, @Param("excluded") UUID excluded);
}
