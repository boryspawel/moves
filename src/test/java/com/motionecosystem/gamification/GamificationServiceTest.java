package com.motionecosystem.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.trainingexecution.ExecutionQualificationPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class GamificationServiceTest {
    private final GamificationProfileRepository profiles = mock(GamificationProfileRepository.class);
    private final PointRuleVersionRepository rules = mock(PointRuleVersionRepository.class);
    private final GamificationLedgerEntryRepository ledger = mock(GamificationLedgerEntryRepository.class);
    private final RankingProjectionRepository rankings = mock(RankingProjectionRepository.class);
    private final CurrentAccountService accounts = mock(CurrentAccountService.class);
    private final ExecutionQualificationPort executions = mock(ExecutionQualificationPort.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void qualificationReturnsPersistedAwardWhenConcurrentFirstProfileRaceViolatesUniqueConstraint() {
        UUID executionId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID awardId = UUID.randomUUID();
        GamificationLedgerEntry persisted = GamificationLedgerEntry.award(awardId, UUID.randomUUID(), executionId,
                "activity", ruleId, 50, PointLedgerEntry.Reason.SESSION_COMPLETION, clock.instant(), "participant");
        when(transactions.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate award"));
        when(ledger.findBySourceExecutionIdAndEntryType(executionId, PointLedgerEntry.EntryType.AWARD))
                .thenReturn(java.util.List.of(persisted));
        when(rules.findById(ruleId)).thenReturn(Optional.of(rule(ruleId, "v1")));

        GamificationService.QualificationView result = service().qualify("participant", executionId, "retry-key");

        assertThat(result).isEqualTo(new GamificationService.QualificationView(
                awardId, executionId, 50, "SESSION_COMPLETION", "v1"));
    }

    @Test
    void publishingFlushesDeactivationBeforePersistingReplacementActiveRule() {
        PointRuleVersion active = rule(UUID.randomUUID(), "v1");
        when(rules.findActiveForUpdate()).thenReturn(Optional.of(active));
        when(rules.findByActiveTrue()).thenReturn(Optional.of(active));

        service().publishRule("administrator", new GamificationService.RuleCommand(
                "v2", 50, 500, 1000, 0, 5, 1, 50));

        InOrder order = inOrder(rules);
        order.verify(rules).findActiveForUpdate();
        order.verify(rules).flush();
        order.verify(rules).save(any(PointRuleVersion.class));
    }

    private GamificationService service() {
        return new GamificationService(profiles, rules, ledger, rankings, accounts, executions, audit, clock, transactions);
    }

    private PointRuleVersion rule(UUID id, String versionName) {
        return new PointRuleVersion(id, versionName, 50, 500, 1000, 0, 5, 1, 50, "administrator", clock.instant());
    }
}
