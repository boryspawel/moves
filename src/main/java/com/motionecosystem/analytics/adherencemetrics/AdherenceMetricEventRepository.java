package com.motionecosystem.analytics.adherencemetrics;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdherenceMetricEventRepository extends JpaRepository<AdherenceMetricEvent, UUID> {
    boolean existsByDeduplicationKey(String deduplicationKey);
    long deleteByExpiresAtBefore(Instant expiresAt);
}
