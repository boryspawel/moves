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
    @Query("select o from GoalObservation o where o.goalId = :goalId and (:outcomeId is null or o.outcomeId = :outcomeId) and (:measuredAt is null or o.measuredAt < :measuredAt or (o.measuredAt = :measuredAt and (o.recordedAt < :recordedAt or (o.recordedAt = :recordedAt and o.id < :id)))) order by o.measuredAt desc, o.recordedAt desc, o.id desc")
    List<GoalObservation> seek(@Param("goalId") UUID goalId, @Param("outcomeId") UUID outcomeId, @Param("measuredAt") java.time.Instant measuredAt, @Param("recordedAt") java.time.Instant recordedAt, @Param("id") UUID id, Pageable pageable);
}
