package com.motionecosystem.participantgoals;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface GoalObservationIdempotencyRepository extends JpaRepository<GoalObservationIdempotency, UUID> {
    Optional<GoalObservationIdempotency> findBySpecialistAccountIdAndOperationAndIdempotencyKey(UUID specialistAccountId, String operation, String idempotencyKey);
}
