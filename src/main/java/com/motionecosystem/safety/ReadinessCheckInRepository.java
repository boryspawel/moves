package com.motionecosystem.safety;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReadinessCheckInRepository extends JpaRepository<ReadinessCheckIn, UUID> {
    Optional<ReadinessCheckIn> findFirstByAccountIdOrderByRecordedAtDesc(UUID accountId);
}
