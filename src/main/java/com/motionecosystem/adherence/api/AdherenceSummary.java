package com.motionecosystem.adherence.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable, current-active-plan adherence projection; it is not historical-plan reporting. */
public record AdherenceSummary(
        String scope,
        String dataStatus,
        String timeZone,
        LocalDate from,
        LocalDate to,
        int plannedSessions,
        int undatedSessionsExcluded,
        int notStartedSessions,
        int inProgressSessions,
        int pausedSessions,
        int completedSessions,
        int partialSessions,
        int skippedSessions,
        int stoppedSessions,
        int abandonedSessions,
        BigDecimal completedPlannedPercent) {

    public static AdherenceSummary noData() {
        return new AdherenceSummary("CURRENT_ACTIVE_PLANS", "NO_DATA", null, null, null,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }
}
