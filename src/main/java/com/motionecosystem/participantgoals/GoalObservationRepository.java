package com.motionecosystem.participantgoals;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GoalObservationRepository extends JpaRepository<GoalObservation, UUID> {
    Optional<GoalObservation> findTopByGoalIdAndOutcomeIdOrderByMeasuredAtDescRecordedAtDescIdDesc(UUID goalId, UUID outcomeId);
    List<GoalObservation> findByGoalIdOrderByMeasuredAtDescRecordedAtDescIdDesc(UUID goalId, Pageable pageable);
    List<GoalObservation> findByGoalIdAndOutcomeIdOrderByMeasuredAtDescRecordedAtDescIdDesc(UUID goalId, UUID outcomeId, Pageable pageable);
    long countByGoalIdAndOutcomeId(UUID goalId, UUID outcomeId);
    @Query("""
            select o from GoalObservation o where o.goalId = :goalId and o.outcomeId in :outcomeIds
            and (select count(newer) from GoalObservation newer where newer.goalId = o.goalId and newer.outcomeId = o.outcomeId
                 and (newer.measuredAt > o.measuredAt or (newer.measuredAt = o.measuredAt and (newer.recordedAt > o.recordedAt or (newer.recordedAt = o.recordedAt and newer.id > o.id))))) < 2
            order by o.outcomeId asc, o.measuredAt desc, o.recordedAt desc, o.id desc
            """)
    List<GoalObservation> findLatestTwoByGoalIdAndOutcomeIdIn(@Param("goalId") UUID goalId, @Param("outcomeIds") Collection<UUID> outcomeIds);
    interface OutcomeObservationCount { UUID getOutcomeId(); long getObservationCount(); }
    @Query("select o.outcomeId as outcomeId, count(o) as observationCount from GoalObservation o where o.goalId = :goalId group by o.outcomeId")
    List<OutcomeObservationCount> countByGoalIdGroupedByOutcomeId(@Param("goalId") UUID goalId);
    @Query("select o from GoalObservation o where o.goalId = :goalId and (o.measuredAt < :measuredAt or (o.measuredAt = :measuredAt and (o.recordedAt < :recordedAt or (o.recordedAt = :recordedAt and o.id < :id)))) order by o.measuredAt desc, o.recordedAt desc, o.id desc")
    List<GoalObservation> seekAfterAllOutcomes(@Param("goalId") UUID goalId, @Param("measuredAt") java.time.Instant measuredAt, @Param("recordedAt") java.time.Instant recordedAt, @Param("id") UUID id, Pageable pageable);

    @Query("select o from GoalObservation o where o.goalId = :goalId and o.outcomeId = :outcomeId and (o.measuredAt < :measuredAt or (o.measuredAt = :measuredAt and (o.recordedAt < :recordedAt or (o.recordedAt = :recordedAt and o.id < :id)))) order by o.measuredAt desc, o.recordedAt desc, o.id desc")
    List<GoalObservation> seekAfterOutcome(@Param("goalId") UUID goalId, @Param("outcomeId") UUID outcomeId, @Param("measuredAt") java.time.Instant measuredAt, @Param("recordedAt") java.time.Instant recordedAt, @Param("id") UUID id, Pageable pageable);
}
