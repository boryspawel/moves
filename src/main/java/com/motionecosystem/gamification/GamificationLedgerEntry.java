package com.motionecosystem.gamification;

import com.motionecosystem.gamification.PointLedgerEntry.EntryType;
import com.motionecosystem.gamification.PointLedgerEntry.Reason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_ledger_entry", schema = "gamification")
class GamificationLedgerEntry {
    @Id private UUID id;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "source_execution_id", nullable = false) private UUID sourceExecutionId;
    @Column(name = "activity_key", nullable = false, length = 2000) private String activityKey;
    @Column(name = "rule_version_id", nullable = false) private UUID ruleVersionId;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false) private EntryType entryType;
    @Column(nullable = false) private int points;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Reason reason;
    @Column(length = 500) private String explanation;
    @Column(name = "reverses_entry_id") private UUID reversesEntryId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "created_by_subject", nullable = false) private String createdBySubject;

    protected GamificationLedgerEntry() {
    }

    private GamificationLedgerEntry(UUID id, UUID accountId, UUID sourceExecutionId, String activityKey,
                                    UUID ruleVersionId, EntryType entryType, int points, Reason reason,
                                    String explanation, UUID reversesEntryId, Instant occurredAt, String createdBySubject) {
        this.id = id; this.accountId = accountId; this.sourceExecutionId = sourceExecutionId; this.activityKey = activityKey;
        this.ruleVersionId = ruleVersionId; this.entryType = entryType; this.points = points; this.reason = reason;
        this.explanation = explanation; this.reversesEntryId = reversesEntryId; this.occurredAt = occurredAt;
        this.createdBySubject = createdBySubject;
    }

    static GamificationLedgerEntry award(UUID id, UUID accountId, UUID sourceExecutionId, String activityKey,
                                         UUID ruleVersionId, int points, Reason reason, Instant occurredAt, String subject) {
        return new GamificationLedgerEntry(id, accountId, sourceExecutionId, activityKey, ruleVersionId,
                EntryType.AWARD, points, reason, null, null, occurredAt, subject);
    }

    static GamificationLedgerEntry reversal(UUID id, GamificationLedgerEntry original, String explanation,
                                            Instant occurredAt, String subject) {
        return new GamificationLedgerEntry(id, original.accountId, original.sourceExecutionId, original.activityKey,
                original.ruleVersionId, EntryType.REVERSAL, -original.points, Reason.REVERSAL, explanation,
                original.id, occurredAt, subject);
    }

    UUID id() { return id; }
    UUID accountId() { return accountId; }
    UUID sourceExecutionId() { return sourceExecutionId; }
    UUID ruleVersionId() { return ruleVersionId; }
    EntryType entryType() { return entryType; }
    int points() { return points; }
    Reason reason() { return reason; }
    UUID reversesEntryId() { return reversesEntryId; }
    Instant occurredAt() { return occurredAt; }
}
