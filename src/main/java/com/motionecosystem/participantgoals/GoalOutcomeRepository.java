package com.motionecosystem.participantgoals;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface GoalOutcomeRepository extends JpaRepository<GoalOutcome, UUID> {
    List<GoalOutcome> findByGoalIdOrderByPositionAsc(UUID goalId);
    Optional<GoalOutcome> findByIdAndGoalId(UUID id, UUID goalId);
}
