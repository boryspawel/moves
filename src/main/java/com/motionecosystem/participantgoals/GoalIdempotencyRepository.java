package com.motionecosystem.participantgoals;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface GoalIdempotencyRepository extends JpaRepository<GoalIdempotency, UUID> {
    Optional<GoalIdempotency> findBySpecialistAccountIdAndOperationAndIdempotencyKey(UUID specialistId, String operation, String key);
}
