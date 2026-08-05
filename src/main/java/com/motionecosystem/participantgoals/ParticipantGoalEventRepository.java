package com.motionecosystem.participantgoals;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ParticipantGoalEventRepository extends JpaRepository<ParticipantGoalEvent, UUID> {
    Optional<ParticipantGoalEvent> findByIdAndParticipantId(UUID id, UUID participantId);
    @Query("select e from ParticipantGoalEvent e where e.participantId = :participant and e.effectiveAt >= :from and e.effectiveAt < :to "
            + "order by e.effectiveAt desc, e.recordedAt desc, e.id asc")
    List<ParticipantGoalEvent> timelineWithoutCursor(@Param("participant") UUID participant, @Param("from") Instant from,
            @Param("to") Instant to, Pageable page);

    @Query("select e from ParticipantGoalEvent e where e.participantId = :participant and e.effectiveAt >= :from and e.effectiveAt < :to "
            + "and (e.effectiveAt < :effective or (e.effectiveAt = :effective and (e.recordedAt < :recorded or (e.recordedAt = :recorded and e.id > :id)))) "
            + "order by e.effectiveAt desc, e.recordedAt desc, e.id asc")
    List<ParticipantGoalEvent> timelineAfterCursor(@Param("participant") UUID participant, @Param("from") Instant from, @Param("to") Instant to,
            @Param("effective") Instant effective, @Param("recorded") Instant recorded, @Param("id") UUID id, Pageable page);
}
