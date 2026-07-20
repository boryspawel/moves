package com.motionecosystem.gamification;

import java.time.Instant;
import java.util.UUID;

public record PointLedgerEntry(UUID id, UUID accountId, UUID sourceExecutionId,
                               UUID ruleVersionId, EntryType type, int points,
                               Reason reason, UUID reversesEntryId, Instant occurredAt) {

    public enum EntryType { AWARD, REVERSAL }

    public enum Reason {
        SESSION_COMPLETION,
        DIMINISHING_RETURN,
        DAILY_LIMIT,
        WEEKLY_LIMIT,
        COOLDOWN,
        REVERSAL
    }
}
