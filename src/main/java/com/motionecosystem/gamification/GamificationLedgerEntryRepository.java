package com.motionecosystem.gamification;

import com.motionecosystem.gamification.PointLedgerEntry.EntryType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface GamificationLedgerEntryRepository extends JpaRepository<GamificationLedgerEntry, UUID> {
    List<GamificationLedgerEntry> findBySourceExecutionIdAndEntryType(UUID sourceExecutionId, EntryType entryType);
    List<GamificationLedgerEntry> findByAccountIdOrderByOccurredAtDescIdAsc(UUID accountId);
    Optional<GamificationLedgerEntry> findTopByAccountIdAndEntryTypeAndPointsGreaterThanOrderByOccurredAtDesc(UUID accountId, EntryType entryType, int points);
    long countByAccountIdAndActivityKeyAndEntryTypeAndPointsGreaterThanAndOccurredAtGreaterThanEqual(UUID accountId, String activityKey, EntryType entryType, int points, Instant occurredAt);
    boolean existsByReversesEntryId(UUID reversesEntryId);

    @Query("select coalesce(sum(e.points), 0) from GamificationLedgerEntry e where e.accountId = :accountId and e.entryType = :entryType and e.points > 0 and e.occurredAt >= :occurredAt")
    long sumPositivePointsSince(UUID accountId, EntryType entryType, Instant occurredAt);

    @Query("select coalesce(sum(e.points), 0) from GamificationLedgerEntry e where e.accountId = :accountId")
    long totalPoints(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GamificationLedgerEntry> findByIdAndEntryTypeAndPointsGreaterThan(UUID id, EntryType entryType, int points);
}
