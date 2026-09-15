package com.motionecosystem.adherence.api;

import java.time.LocalDate;
import java.util.UUID;

/** Current-active-plan summary owned by adherence. */
public interface AdherenceSummaryQueryPort {
    AdherenceSummary summarize(UUID participantId, LocalDate from, LocalDate to);
}
