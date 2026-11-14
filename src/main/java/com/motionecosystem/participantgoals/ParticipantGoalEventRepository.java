package com.motionecosystem.participantgoals;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ParticipantGoalEventRepository extends JpaRepository<ParticipantGoalEvent, UUID> {
    @Query("select e from ParticipantGoalEvent e, ParticipantGoal g where e.goalId = g.id "
            + "and e.id = :id and e.participantId = :participant and g.specialistAccountId = :specialist")
    Optional<ParticipantGoalEvent> findOwnedEvent(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
                                                   @Param("id") UUID id);
    @Query("select e from ParticipantGoalEvent e, ParticipantGoal g where e.goalId = g.id "
            + "and g.specialistAccountId = :specialist and e.participantId = :participant "
            + "and e.effectiveAt >= :from and e.effectiveAt < :to order by e.effectiveAt desc, e.recordedAt desc, e.id asc")
    List<ParticipantGoalEvent> timelineWithoutCursor(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, Pageable page);

    @Query("select e from ParticipantGoalEvent e, ParticipantGoal g where e.goalId = g.id "
            + "and g.specialistAccountId = :specialist and e.participantId = :participant "
            + "and e.effectiveAt >= :from and e.effectiveAt < :to "
            + "and (e.effectiveAt < :effective or (e.effectiveAt = :effective and (e.recordedAt < :recorded or (e.recordedAt = :recorded and e.id > :id)))) "
            + "order by e.effectiveAt desc, e.recordedAt desc, e.id asc")
    List<ParticipantGoalEvent> timelineAfterCursor(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to, @Param("effective") Instant effective,
            @Param("recorded") Instant recorded, @Param("id") UUID id, Pageable page);

    @Query("select e from ParticipantGoalEvent e, ParticipantGoal g where e.goalId = g.id and e.participantId = :participant "
            + "and g.specialistAccountId = :specialist and e.effectiveAt >= :from and e.effectiveAt < :to "
            + "order by e.effectiveAt desc, e.recordedAt desc, e.id asc")
    List<ParticipantGoalEvent> findHistoryCandidates(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("from") Instant from, @Param("to") Instant to);
    @Query("select e from ParticipantGoalEvent e, ParticipantGoal g where e.goalId = g.id and e.participantId = :participant "
            + "and g.specialistAccountId = :specialist and e.id in :eventIds")
    List<ParticipantGoalEvent> findHistoryByEventIds(@Param("specialist") UUID specialist, @Param("participant") UUID participant,
            @Param("eventIds") Set<UUID> eventIds);
}
