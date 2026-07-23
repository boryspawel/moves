package com.motionecosystem.calendar;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppointmentIdempotencyRepository extends JpaRepository<AppointmentIdempotency, UUID> {
    Optional<AppointmentIdempotency> findBySpecialistAccountIdAndOperationAndIdempotencyKey(UUID specialistAccountId, String operation, String idempotencyKey);
}
