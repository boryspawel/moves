package com.motionecosystem.gamification;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GamificationService {
    private final GamificationProfileRepository profiles;
    private final PointRuleVersionRepository rules;
    private final GamificationLedgerEntryRepository ledger;
    private final RankingProjectionRepository rankings;
    private final CurrentAccountService accounts;
    private final ExecutionQualificationPort executions;
    private final AuditRecorder audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Transactional
    public ProfileView updateProfile(String subject, ProfileCommand command) {
        CurrentAccount account = participant(subject);
        if (command == null) throw badRequest("gamification profile is required");
        String pseudonym = optionalPseudonym(command.pseudonym());
        if (command.rankingVisible() && (!command.enabled() || pseudonym == null)) {
            throw badRequest("visible ranking requires enabled gamification and a pseudonym");
        }
        Instant now = clock.instant();
        GamificationProfile profile = profiles.findById(account.id()).orElse(null);
        Instant enabledAt = command.enabled() && profile != null && profile.enabled() ? profile.enabledAt() : command.enabled() ? now : null;
        if (profile == null) profile = new GamificationProfile(account.id(), command.enabled(), pseudonym, command.enabled() && command.rankingVisible(), enabledAt, now);
        else profile.update(command.enabled(), pseudonym, command.enabled() && command.rankingVisible(), enabledAt, now);
        profiles.save(profile);
        rebuildAccount(account.id());
        audit.record(subject, "GAMIFICATION_PROFILE_UPDATED", "GamificationProfile", account.id());
        return profile(account.id());
    }

    public QualificationView qualify(String subject, UUID executionId, String idempotencyKey) {
        try {
            return transactions.execute(status -> qualifyInTransaction(subject, executionId, idempotencyKey));
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            return ledger.findBySourceExecutionIdAndEntryType(executionId, EntryType.AWARD).stream()
                    .findFirst()
                    .map(this::qualification)
                    .orElseThrow(() -> race);
        }
    }

    private QualificationView qualifyInTransaction(String subject, UUID executionId, String idempotencyKey) {
        CurrentAccount account = participant(subject);
        requiredText(idempotencyKey, 120, "Idempotency-Key");
        QualifyingExecution execution = executions.requireDeclared(executionId);
        if (!execution.participantAccountId().equals(account.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "execution belongs to another participant");
        List<GamificationLedgerEntry> existing = ledger.findBySourceExecutionIdAndEntryType(executionId, EntryType.AWARD);
        if (!existing.isEmpty()) return qualification(existing.getFirst());
        GamificationProfile profile = profiles.findByAccountIdForUpdate(account.id()).orElse(null);
        existing = ledger.findBySourceExecutionIdAndEntryType(executionId, EntryType.AWARD);
        if (!existing.isEmpty()) return qualification(existing.getFirst());
        if (profile == null || !profile.enabled() || execution.recordedAt().isBefore(profile.enabledAt())) return new QualificationView(null, executionId, 0, "NOT_OPTED_IN", null);
        RuleView rule = activeRule();
        Instant now = clock.instant();
        PointPolicy.Decision decision = PointPolicy.decide(rule.policy(), policyState(account.id(), execution.activityKey(), rule, now));
        UUID ledgerId = UUID.randomUUID();
        ledger.save(GamificationLedgerEntry.award(ledgerId, account.id(), executionId, execution.activityKey(), rule.id(), decision.points(), decision.reason(), now, subject));
        rebuildAccount(account.id());
        audit.record(subject, "POINTS_QUALIFIED", "PointLedgerEntry", ledgerId);
        return new QualificationView(ledgerId, executionId, decision.points(), decision.reason().name(), rule.versionName());
    }

    @Transactional(readOnly = true)
    public GamificationProgressView progress(String subject) {
        CurrentAccount account = participant(subject);
        List<LedgerView> entries = ledger.findByAccountIdOrderByOccurredAtDescIdAsc(account.id()).stream().map(this::ledgerView).toList();
        return new GamificationProgressView(profile(account.id()), ledger.totalPoints(account.id()), entries);
    }

    @Transactional(readOnly = true)
    public List<RankingRow> ranking() {
        List<RankingProjection> rows = rankings.findRanking(PageRequest.of(0, 100));
        return java.util.stream.IntStream.range(0, rows.size()).mapToObj(i -> new RankingRow(i + 1, rows.get(i).pseudonym(), rows.get(i).points())).toList();
    }

    @Transactional
    public RuleView publishRule(String subject, RuleCommand command) {
        validateRule(command);
        if (rules.findActiveForUpdate().map(rule -> {
            rule.deactivate();
            return true;
        }).orElse(false)) {
            rules.flush();
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        rules.save(new PointRuleVersion(id, requiredText(command.versionName(), 80, "rule version"), command.basePoints(), command.dailyLimit(), command.weeklyLimit(), command.cooldownSeconds(), command.repeatWindowDays(), command.fullRewardOccurrences(), command.reducedRewardPercent(), subject, now));
        audit.record(subject, "POINT_RULE_PUBLISHED", "PointRuleVersion", id);
        return activeRule();
    }

    @Transactional
    public LedgerView reverse(String subject, UUID entryId, ReversalCommand command) {
        String explanation = requiredText(command == null ? null : command.reason(), 500, "reversal reason");
        GamificationLedgerEntry original = ledger.findByIdAndEntryTypeAndPointsGreaterThan(entryId, EntryType.AWARD, 0).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "reversible point entry not found"));
        if (ledger.existsByReversesEntryId(original.id())) throw new ResponseStatusException(HttpStatus.CONFLICT, "point entry was already reversed");
        UUID reversalId = UUID.randomUUID();
        Instant now = clock.instant();
        GamificationLedgerEntry reversal = GamificationLedgerEntry.reversal(reversalId, original, explanation, now, subject);
        ledger.save(reversal);
        rebuildAccount(original.accountId());
        audit.record(subject, "POINT_ENTRY_REVERSED", "PointLedgerEntry", reversalId);
        return ledgerView(reversal);
    }

    @Transactional
    public List<RankingRow> rebuildRanking(String subject) {
        rankings.deleteAllInBatch();
        Instant now = clock.instant();
        profiles.findByEnabledTrueAndRankingVisibleTrueAndPseudonymIsNotNull().forEach(profile -> rankings.save(new RankingProjection(profile.accountId(), profile.pseudonym(), ledger.totalPoints(profile.accountId()), now)));
        audit.record(subject, "RANKING_REBUILT", "RankingProjection", null);
        return ranking();
    }

    private PointPolicy.State policyState(UUID accountId, String activityKey, RuleView rule, Instant now) {
        Instant lastAward = ledger.findTopByAccountIdAndEntryTypeAndPointsGreaterThanOrderByOccurredAtDesc(accountId, EntryType.AWARD, 0).map(GamificationLedgerEntry::occurredAt).orElse(null);
        Duration since = lastAward == null ? null : Duration.between(lastAward, now);
        int repeats = Math.toIntExact(ledger.countByAccountIdAndActivityKeyAndEntryTypeAndPointsGreaterThanAndOccurredAtGreaterThanEqual(accountId, activityKey, EntryType.AWARD, 0, now.minus(Duration.ofDays(rule.repeatWindowDays()))));
        return new PointPolicy.State(since, repeats, positivePoints(accountId, now.minus(Duration.ofDays(1))), positivePoints(accountId, now.minus(Duration.ofDays(7))));
    }

    private int positivePoints(UUID accountId, Instant since) { return Math.toIntExact(ledger.sumPositivePointsSince(accountId, EntryType.AWARD, since)); }

    private RuleView activeRule() {
        PointRuleVersion rule = rules.findByActiveTrue().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "no active point rule is published"));
        return new RuleView(rule.id(), rule.versionName(), rule.basePoints(), rule.dailyLimit(), rule.weeklyLimit(), rule.cooldownSeconds(), rule.repeatWindowDays(), rule.fullRewardOccurrences(), rule.reducedRewardPercent());
    }

    private void rebuildAccount(UUID accountId) {
        rankings.deleteById(accountId);
        profiles.findById(accountId).filter(profile -> profile.enabled() && profile.rankingVisible() && profile.pseudonym() != null).ifPresent(profile -> rankings.save(new RankingProjection(accountId, profile.pseudonym(), ledger.totalPoints(accountId), clock.instant())));
    }

    private ProfileView profile(UUID accountId) {
        return profiles.findById(accountId).map(p -> new ProfileView(p.enabled(), p.pseudonym(), p.rankingVisible(), p.enabledAt(), p.updatedAt())).orElse(new ProfileView(false, null, false, null, null));
    }

    private QualificationView qualification(GamificationLedgerEntry entry) {
        String version = rules.findById(entry.ruleVersionId()).map(PointRuleVersion::versionName).orElse(null);
        return new QualificationView(entry.id(), entry.sourceExecutionId(), entry.points(), entry.reason().name(), version);
    }

    private LedgerView ledgerView(GamificationLedgerEntry entry) { return new LedgerView(entry.id(), entry.sourceExecutionId(), entry.entryType(), entry.points(), entry.reason(), entry.reversesEntryId(), entry.occurredAt()); }
    private CurrentAccount participant(String subject) {
        CurrentAccount account = accounts.requireActive(subject);
        if (account.profileType() != ProfileType.PARTICIPANT) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        return account;
    }
    private static void validateRule(RuleCommand command) {
        if (command == null || command.basePoints() <= 0 || command.dailyLimit() <= 0 || command.weeklyLimit() < command.dailyLimit() || command.cooldownSeconds() < 0 || command.repeatWindowDays() <= 0 || command.fullRewardOccurrences() <= 0 || command.reducedRewardPercent() < 1 || command.reducedRewardPercent() > 100) throw badRequest("point rule values are outside range");
    }
    private static String optionalPseudonym(String value) { return value == null || value.isBlank() ? null : requiredText(value, 80, "pseudonym"); }
    private static String requiredText(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) throw badRequest(field + " is required and too long values are rejected");
        return normalized;
    }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record ProfileCommand(boolean enabled, String pseudonym, boolean rankingVisible) { }
    public record ProfileView(boolean enabled, String pseudonym, boolean rankingVisible, Instant enabledAt, Instant updatedAt) { }
    public record RuleCommand(String versionName, int basePoints, int dailyLimit, int weeklyLimit, int cooldownSeconds, int repeatWindowDays, int fullRewardOccurrences, int reducedRewardPercent) { }
    public record RuleView(UUID id, String versionName, int basePoints, int dailyLimit, int weeklyLimit, int cooldownSeconds, int repeatWindowDays, int fullRewardOccurrences, int reducedRewardPercent) {
        PointPolicy.Rule policy() { return new PointPolicy.Rule(basePoints, dailyLimit, weeklyLimit, cooldownSeconds, fullRewardOccurrences, reducedRewardPercent); }
    }
    public record QualificationView(UUID ledgerEntryId, UUID sourceExecutionId, int points, String outcome, String ruleVersion) { }
    public record LedgerView(UUID id, UUID sourceExecutionId, EntryType type, int points, Reason reason, UUID reversesEntryId, Instant occurredAt) { }
    public record GamificationProgressView(ProfileView profile, long points, List<LedgerView> ledger) { }
    public record RankingRow(int position, String pseudonym, long points) { }
    public record ReversalCommand(String reason) { }
}
