package com.motionecosystem.participantgoals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TargetComparatorTest {
    @Test
    void comparesBothExplicitTargetDirectionsAndMissingData() {
        assertThat(TargetComparator.AT_LEAST.progress(BigDecimal.TEN, new BigDecimal("10")))
                .isEqualTo(TargetComparator.ProgressState.TARGET_REACHED);
        assertThat(TargetComparator.AT_LEAST.progress(BigDecimal.TEN, new BigDecimal("9")))
                .isEqualTo(TargetComparator.ProgressState.IN_PROGRESS);
        assertThat(TargetComparator.AT_MOST.progress(BigDecimal.TEN, new BigDecimal("11")))
                .isEqualTo(TargetComparator.ProgressState.IN_PROGRESS);
        assertThat(TargetComparator.AT_MOST.progress(BigDecimal.TEN, new BigDecimal("9")))
                .isEqualTo(TargetComparator.ProgressState.TARGET_REACHED);
        assertThat(TargetComparator.AT_LEAST.progress(BigDecimal.TEN, null))
                .isEqualTo(TargetComparator.ProgressState.NO_DATA);
    }
}
