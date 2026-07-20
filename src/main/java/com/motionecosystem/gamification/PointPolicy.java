package com.motionecosystem.gamification;

import java.time.Duration;

import com.motionecosystem.gamification.PointLedgerEntry.Reason;

public final class PointPolicy {

    private PointPolicy() {
    }

    public static Decision decide(Rule rule, State state) {
        if (state.sinceLastPositiveAward() != null
                && state.sinceLastPositiveAward().compareTo(Duration.ofSeconds(rule.cooldownSeconds())) < 0) {
            return new Decision(0, Reason.COOLDOWN);
        }
        int candidate = state.repeatAwardsInWindow() >= rule.fullRewardOccurrences()
                ? rule.basePoints() * rule.reducedRewardPercent() / 100
                : rule.basePoints();
        boolean diminished = candidate < rule.basePoints();
        int dailyRemaining = Math.max(0, rule.dailyLimit() - state.positivePointsLastDay());
        if (dailyRemaining == 0) {
            return new Decision(0, Reason.DAILY_LIMIT);
        }
        int weeklyRemaining = Math.max(0, rule.weeklyLimit() - state.positivePointsLastWeek());
        if (weeklyRemaining == 0) {
            return new Decision(0, Reason.WEEKLY_LIMIT);
        }
        int awarded = Math.min(candidate, Math.min(dailyRemaining, weeklyRemaining));
        if (awarded < candidate) {
            return new Decision(awarded, dailyRemaining <= weeklyRemaining ? Reason.DAILY_LIMIT : Reason.WEEKLY_LIMIT);
        }
        return new Decision(awarded, diminished ? Reason.DIMINISHING_RETURN : Reason.SESSION_COMPLETION);
    }

    public record Rule(int basePoints, int dailyLimit, int weeklyLimit, int cooldownSeconds,
                       int fullRewardOccurrences, int reducedRewardPercent) {
    }

    public record State(Duration sinceLastPositiveAward, int repeatAwardsInWindow,
                        int positivePointsLastDay, int positivePointsLastWeek) {
    }

    public record Decision(int points, Reason reason) {
    }
}
