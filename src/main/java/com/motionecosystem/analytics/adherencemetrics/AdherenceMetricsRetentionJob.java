package com.motionecosystem.analytics.adherencemetrics;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily, idempotent removal of expired adherence metric events. */
@Component
@RequiredArgsConstructor
class AdherenceMetricsRetentionJob {
    private final AdherenceMetricsService metrics;

    @Scheduled(cron = "${moves.analytics.adherence-metrics-retention-cron:0 0 3 * * *}")
    void purgeExpired() {
        metrics.purgeExpired();
    }
}
