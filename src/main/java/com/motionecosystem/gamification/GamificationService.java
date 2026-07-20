package com.motionecosystem.gamification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.gamification.PointLedgerEntry.EntryType;
import com.motionecosystem.gamification.PointLedgerEntry.Reason;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.trainingexecution.ExecutionQualificationPort;
import com.motionecosystem.trainingexecution.ExecutionQualificationPort.QualifyingExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GamificationService {

    private final JdbcTemplate jdbc;
    private final CurrentAccountService accounts;
    private final ExecutionQualificationPort executions;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public ProfileView updateProfile(String subject, ProfileCommand command) {
        CurrentAccount account = participant(subject);
        if (command == null) {
            throw badRequest("gamification profile is required");
        }
        String pseudonym = optionalPseudonym(command.pseudonym());
        if (command.rankingVisible() && (!command.enabled() || pseudonym == null)) {
            throw badRequest("visible ranking requires enabled gamification and a pseudonym");
        }
        Instant now = clock.instant();
        Instant enabledAt = command.enabled()
                ? jdbc.query("SELECT enabled_at FROM gamification.gamification_profile WHERE account_id = ? AND enabled",
                        (rs, row) -> rs.getTimestamp(1).toInstant(), account.id()).stream().findFirst().orElse(now)
                : null;
        jdbc.update("""
                INSERT INTO gamification.gamification_profile
                    (account_id, enabled, pseudonym, ranking_visible, enabled_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET
                    enabled = excluded.enabled,
                    pseudonym = excluded.pseudonym,
                    ranking_visible = excluded.ranking_visible,
                    enabled_at = excluded.enabled_at,
                    updated_at = excluded.updated_at
                """, account.id(), command.enabled(), pseudonym,
                command.enabled() && command.rankingVisible(), timestamp(enabledAt), Timestamp.from(now));
        rebuildAccount(account.id());
        audit.record(subject, "GAMIFICATION_PROFILE_UPDATED", "GamificationProfile", account.id());
        return profile(account.id());
    }

    @Transactional
    public QualificationView qualify(String subject, UUID executionId, String idempotencyKey) {
        CurrentAccount account = participant(subject);
        requiredText(idempotencyKey, 120, "Idempotency-Key");
        QualifyingExecution execution = executions.requireDeclared(executionId);
        if (!execution.participantAccountId().equals(account.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "execution belongs to another participant");
        }
        List<PointLedgerEntry> existing = ledgerForExecution(executionId, EntryType.AWARD);
        if (!existing.isEmpty()) {
            return qualification(existing.getFirst());
        }

        List<ProfileState> profiles = jdbc.query("""
                SELECT enabled, enabled_at FROM gamification.gamification_profile
                WHERE account_id = ? FOR UPDATE
                """, (rs, row) -> new ProfileState(rs.getBoolean("enabled"),
                rs.getTimestamp("enabled_at") == null ? null : rs.getTimestamp("enabled_at").toInstant()), account.id());
        if (profiles.isEmpty() || !profiles.getFirst().enabled()
                || execution.recordedAt().isBefore(profiles.getFirst().enabledAt())) {
            return new QualificationView(null, executionId, 0, "NOT_OPTED_IN", null);
        }

        RuleView rule = activeRule();
        Instant now = clock.instant();
        PointPolicy.State state = policyState(account.id(), execution.activityKey(), rule, now);
        PointPolicy.Decision decision = PointPolicy.decide(rule.policy(), state);
        UUID ledgerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gamification.point_ledger_entry
                    (id, account_id, source_execution_id, activity_key, rule_version_id,
                     entry_type, points, reason, occurred_at, created_by_subject)
                VALUES (?, ?, ?, ?, ?, 'AWARD', ?, ?, ?, ?)
                """, ledgerId, account.id(), executionId, execution.activityKey(), rule.id(), decision.points(),
                decision.reason().name(), Timestamp.from(now), subject);
        rebuildAccount(account.id());
        audit.record(subject, "POINTS_QUALIFIED", "PointLedgerEntry", ledgerId);
        return new QualificationView(ledgerId, executionId, decision.points(), decision.reason().name(), rule.versionName());
    }

    @Transactional(readOnly = true)
    public ProgressView progress(String subject) {
        CurrentAccount account = participant(subject);
        ProfileView profile = profile(account.id());
        long points = totalPoints(account.id());
        List<LedgerView> ledger = jdbc.query("""
                SELECT id, source_execution_id, entry_type, points, reason, reverses_entry_id, occurred_at
                FROM gamification.point_ledger_entry
                WHERE account_id = ? ORDER BY occurred_at DESC, id
                """, (rs, row) -> new LedgerView(
                rs.getObject("id", UUID.class), rs.getObject("source_execution_id", UUID.class),
                EntryType.valueOf(rs.getString("entry_type")), rs.getInt("points"),
                Reason.valueOf(rs.getString("reason")), rs.getObject("reverses_entry_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant()), account.id());
        return new ProgressView(profile, points, ledger);
    }

    @Transactional(readOnly = true)
    public List<RankingRow> ranking() {
        return jdbc.query("""
                SELECT pseudonym, points FROM gamification.ranking_projection
                ORDER BY points DESC, lower(pseudonym), account_id LIMIT 100
                """, (rs, row) -> new RankingRow(row + 1, rs.getString("pseudonym"), rs.getLong("points")));
    }

    @Transactional
    public RuleView publishRule(String subject, RuleCommand command) {
        validateRule(command);
        jdbc.update("UPDATE gamification.point_rule_version SET active = false WHERE active");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO gamification.point_rule_version
                    (id, version_name, base_points, daily_limit, weekly_limit, cooldown_seconds,
                     repeat_window_days, full_reward_occurrences, reduced_reward_percent,
                     active, published_by_subject, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                """, id, requiredText(command.versionName(), 80, "rule version"), command.basePoints(),
                command.dailyLimit(), command.weeklyLimit(), command.cooldownSeconds(),
                command.repeatWindowDays(), command.fullRewardOccurrences(), command.reducedRewardPercent(),
                subject, Timestamp.from(now));
        audit.record(subject, "POINT_RULE_PUBLISHED", "PointRuleVersion", id);
        return activeRule();
    }

    @Transactional
    public LedgerView reverse(String subject, UUID entryId, ReversalCommand command) {
        String reasonText = requiredText(command == null ? null : command.reason(), 500, "reversal reason");
        List<LedgerSource> originals = jdbc.query("""
                SELECT id, account_id, source_execution_id, activity_key, rule_version_id, points
                FROM gamification.point_ledger_entry
                WHERE id = ? AND entry_type = 'AWARD' AND points > 0
                """, (rs, row) -> new LedgerSource(
                rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getObject("source_execution_id", UUID.class), rs.getString("activity_key"),
                rs.getObject("rule_version_id", UUID.class), rs.getInt("points")), entryId);
        if (originals.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reversible point entry not found");
        }
        LedgerSource original = originals.getFirst();
        UUID reversalId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO gamification.point_ledger_entry
                        (id, account_id, source_execution_id, activity_key, rule_version_id,
                         entry_type, points, reason, explanation, reverses_entry_id, occurred_at, created_by_subject)
                    VALUES (?, ?, ?, ?, ?, 'REVERSAL', ?, 'REVERSAL', ?, ?, ?, ?)
                    """, reversalId, original.accountId(), original.sourceExecutionId(), original.activityKey(),
                    original.ruleVersionId(), -original.points(), reasonText, original.id(), Timestamp.from(now), subject);
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "point entry was already reversed", duplicate);
        }
        rebuildAccount(original.accountId());
        audit.record(subject, "POINT_ENTRY_REVERSED", "PointLedgerEntry", reversalId);
        return new LedgerView(reversalId, original.sourceExecutionId(), EntryType.REVERSAL,
                -original.points(), Reason.REVERSAL, original.id(), now);
    }

    @Transactional
    public List<RankingRow> rebuildRanking(String subject) {
        jdbc.update("DELETE FROM gamification.ranking_projection");
        jdbc.update("""
                INSERT INTO gamification.ranking_projection (account_id, pseudonym, points, rebuilt_at)
                SELECT profile.account_id, profile.pseudonym, COALESCE(SUM(ledger.points), 0), ?
                FROM gamification.gamification_profile profile
                LEFT JOIN gamification.point_ledger_entry ledger ON ledger.account_id = profile.account_id
                WHERE profile.enabled AND profile.ranking_visible AND profile.pseudonym IS NOT NULL
                GROUP BY profile.account_id, profile.pseudonym
                """, Timestamp.from(clock.instant()));
        audit.record(subject, "RANKING_REBUILT", "RankingProjection", null);
        return ranking();
    }

    private PointPolicy.State policyState(UUID accountId, String activityKey, RuleView rule, Instant now) {
        List<Instant> lastAwards = jdbc.query("""
                SELECT occurred_at FROM gamification.point_ledger_entry
                WHERE account_id = ? AND entry_type = 'AWARD' AND points > 0
                ORDER BY occurred_at DESC LIMIT 1
                """, (rs, row) -> rs.getTimestamp(1).toInstant(), accountId);
        Duration since = lastAwards.isEmpty() ? null : Duration.between(lastAwards.getFirst(), now);
        int repeats = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gamification.point_ledger_entry
                WHERE account_id = ? AND activity_key = ? AND entry_type = 'AWARD' AND points > 0
                  AND occurred_at >= ?
                """, Integer.class, accountId, activityKey,
                Timestamp.from(now.minus(Duration.ofDays(rule.repeatWindowDays()))));
        int daily = positivePoints(accountId, now.minus(Duration.ofDays(1)));
        int weekly = positivePoints(accountId, now.minus(Duration.ofDays(7)));
        return new PointPolicy.State(since, repeats, daily, weekly);
    }

    private int positivePoints(UUID accountId, Instant since) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(points), 0) FROM gamification.point_ledger_entry
                WHERE account_id = ? AND entry_type = 'AWARD' AND points > 0 AND occurred_at >= ?
                """, Integer.class, accountId, Timestamp.from(since));
        return value == null ? 0 : value;
    }

    private RuleView activeRule() {
        List<RuleView> rules = jdbc.query("""
                SELECT id, version_name, base_points, daily_limit, weekly_limit, cooldown_seconds,
                       repeat_window_days, full_reward_occurrences, reduced_reward_percent
                FROM gamification.point_rule_version WHERE active
                """, (rs, row) -> new RuleView(
                rs.getObject("id", UUID.class), rs.getString("version_name"), rs.getInt("base_points"),
                rs.getInt("daily_limit"), rs.getInt("weekly_limit"), rs.getInt("cooldown_seconds"),
                rs.getInt("repeat_window_days"), rs.getInt("full_reward_occurrences"),
                rs.getInt("reduced_reward_percent")));
        if (rules.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no active point rule is published");
        }
        return rules.getFirst();
    }

    private void rebuildAccount(UUID accountId) {
        jdbc.update("DELETE FROM gamification.ranking_projection WHERE account_id = ?", accountId);
        jdbc.update("""
                INSERT INTO gamification.ranking_projection (account_id, pseudonym, points, rebuilt_at)
                SELECT profile.account_id, profile.pseudonym, COALESCE(SUM(ledger.points), 0), ?
                FROM gamification.gamification_profile profile
                LEFT JOIN gamification.point_ledger_entry ledger ON ledger.account_id = profile.account_id
                WHERE profile.account_id = ? AND profile.enabled AND profile.ranking_visible
                  AND profile.pseudonym IS NOT NULL
                GROUP BY profile.account_id, profile.pseudonym
                """, Timestamp.from(clock.instant()), accountId);
    }

    private ProfileView profile(UUID accountId) {
        return jdbc.query("""
                SELECT enabled, pseudonym, ranking_visible, enabled_at, updated_at
                FROM gamification.gamification_profile WHERE account_id = ?
                """, (rs, row) -> new ProfileView(
                rs.getBoolean("enabled"), rs.getString("pseudonym"), rs.getBoolean("ranking_visible"),
                rs.getTimestamp("enabled_at") == null ? null : rs.getTimestamp("enabled_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), accountId).stream().findFirst()
                .orElse(new ProfileView(false, null, false, null, null));
    }

    private long totalPoints(UUID accountId) {
        Long points = jdbc.queryForObject(
                "SELECT COALESCE(SUM(points), 0) FROM gamification.point_ledger_entry WHERE account_id = ?",
                Long.class, accountId);
        return points == null ? 0 : points;
    }

    private List<PointLedgerEntry> ledgerForExecution(UUID executionId, EntryType type) {
        return jdbc.query("""
                SELECT id, account_id, source_execution_id, rule_version_id, entry_type,
                       points, reason, reverses_entry_id, occurred_at
                FROM gamification.point_ledger_entry
                WHERE source_execution_id = ? AND entry_type = ?
                """, (rs, row) -> new PointLedgerEntry(
                rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getObject("source_execution_id", UUID.class), rs.getObject("rule_version_id", UUID.class),
                EntryType.valueOf(rs.getString("entry_type")), rs.getInt("points"),
                Reason.valueOf(rs.getString("reason")), rs.getObject("reverses_entry_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant()), executionId, type.name());
    }

    private QualificationView qualification(PointLedgerEntry entry) {
        String version = jdbc.queryForObject(
                "SELECT version_name FROM gamification.point_rule_version WHERE id = ?",
                String.class, entry.ruleVersionId());
        return new QualificationView(entry.id(), entry.sourceExecutionId(), entry.points(), entry.reason().name(), version);
    }

    private CurrentAccount participant(String subject) {
        CurrentAccount account = accounts.requireActive(subject);
        if (account.profileType() != ProfileType.PARTICIPANT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        }
        return account;
    }

    private static void validateRule(RuleCommand command) {
        if (command == null || command.basePoints() <= 0 || command.dailyLimit() <= 0
                || command.weeklyLimit() < command.dailyLimit() || command.cooldownSeconds() < 0
                || command.repeatWindowDays() <= 0 || command.fullRewardOccurrences() <= 0
                || command.reducedRewardPercent() < 1 || command.reducedRewardPercent() > 100) {
            throw badRequest("point rule values are outside range");
        }
    }

    private static String optionalPseudonym(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, 80, "pseudonym");
    }

    private static String requiredText(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw badRequest(field + " is required and too long values are rejected");
        }
        return normalized;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ProfileState(boolean enabled, Instant enabledAt) {
    }

    private record LedgerSource(UUID id, UUID accountId, UUID sourceExecutionId,
                                String activityKey, UUID ruleVersionId, int points) {
    }

    public record ProfileCommand(boolean enabled, String pseudonym, boolean rankingVisible) {
    }

    public record ProfileView(boolean enabled, String pseudonym, boolean rankingVisible,
                              Instant enabledAt, Instant updatedAt) {
    }

    public record RuleCommand(String versionName, int basePoints, int dailyLimit,
                              int weeklyLimit, int cooldownSeconds, int repeatWindowDays,
                              int fullRewardOccurrences, int reducedRewardPercent) {
    }

    public record RuleView(UUID id, String versionName, int basePoints, int dailyLimit,
                           int weeklyLimit, int cooldownSeconds, int repeatWindowDays,
                           int fullRewardOccurrences, int reducedRewardPercent) {
        PointPolicy.Rule policy() {
            return new PointPolicy.Rule(basePoints, dailyLimit, weeklyLimit, cooldownSeconds,
                    fullRewardOccurrences, reducedRewardPercent);
        }
    }

    public record QualificationView(UUID ledgerEntryId, UUID sourceExecutionId, int points,
                                    String outcome, String ruleVersion) {
    }

    public record LedgerView(UUID id, UUID sourceExecutionId, EntryType type, int points,
                             Reason reason, UUID reversesEntryId, Instant occurredAt) {
    }

    public record ProgressView(ProfileView profile, long points, List<LedgerView> ledger) {
    }

    public record RankingRow(int position, String pseudonym, long points) {
    }

    public record ReversalCommand(String reason) {
    }
}
