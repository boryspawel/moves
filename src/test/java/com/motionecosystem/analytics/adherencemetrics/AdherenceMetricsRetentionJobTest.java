package com.motionecosystem.analytics.adherencemetrics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class AdherenceMetricsRetentionJobTest {

    @Test
    void purgesExpiredMetrics() {
        var metrics = mock(AdherenceMetricsService.class);

        new AdherenceMetricsRetentionJob(metrics).purgeExpired();

        verify(metrics).purgeExpired();
    }
}
