package com.motionecosystem.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.motionecosystem.gamification.PointLedgerEntry.Reason;
import org.junit.jupiter.api.Test;

class PointPolicyTest {

    private static final PointPolicy.Rule RULE = new PointPolicy.Rule(50, 100, 250, 3600, 3, 50);

    @Test
    void cooldownPreventsAnImmediateAward() {
        assertThat(PointPolicy.decide(RULE,
                new PointPolicy.State(Duration.ofMinutes(59), 0, 0, 0)))
                .isEqualTo(new PointPolicy.Decision(0, Reason.COOLDOWN));
    }

    @Test
    void repeatedActivityReceivesConfiguredDiminishingReturn() {
        assertThat(PointPolicy.decide(RULE,
                new PointPolicy.State(Duration.ofHours(2), 3, 0, 0)))
                .isEqualTo(new PointPolicy.Decision(25, Reason.DIMINISHING_RETURN));
    }

    @Test
    void dailyLimitCapsAndThenStopsPoints() {
        assertThat(PointPolicy.decide(RULE,
                new PointPolicy.State(null, 0, 80, 80)))
                .isEqualTo(new PointPolicy.Decision(20, Reason.DAILY_LIMIT));
        assertThat(PointPolicy.decide(RULE,
                new PointPolicy.State(null, 0, 100, 100)))
                .isEqualTo(new PointPolicy.Decision(0, Reason.DAILY_LIMIT));
    }

    @Test
    void weeklyLimitCapsPointsIndependently() {
        assertThat(PointPolicy.decide(RULE,
                new PointPolicy.State(null, 0, 0, 240)))
                .isEqualTo(new PointPolicy.Decision(10, Reason.WEEKLY_LIMIT));
    }
}
