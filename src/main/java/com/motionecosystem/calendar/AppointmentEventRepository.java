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
            select e from AppointmentEvent e where e.specialistAccountId = :specialist and e.participantId = :participant
              and e.effectiveAt >= :from and e.effectiveAt < :to
            order by e.effectiveAt desc, e.recordedAt desc, e.id asc
            """)
    List<AppointmentEvent> findInitial(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select e from AppointmentEvent e where e.specialistAccountId = :specialist and e.participantId = :participant
              and e.effectiveAt >= :from and e.effectiveAt < :to and
              (e.effectiveAt < :effective or (e.effectiveAt = :effective and
                (e.recordedAt < :recorded or (e.recordedAt = :recorded and e.id > :id))))
            order by e.effectiveAt desc, e.recordedAt desc, e.id asc
            """)
    List<AppointmentEvent> findAfter(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, @Param("effective") Instant effective,
            @Param("recorded") Instant recorded, @Param("id") UUID id, Pageable pageable);
}
