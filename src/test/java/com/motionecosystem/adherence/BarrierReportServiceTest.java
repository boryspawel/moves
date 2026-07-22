package com.motionecosystem.adherence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import com.motionecosystem.specialist.api.AdherenceSpecialistSignalPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionAttemptQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BarrierReportServiceTest {
    @Test
    void noTimeOffersApprovedVariantsAndNeverSignalsSpecialistAutomatically() {
        UUID participant = UUID.randomUUID(); UUID revision = UUID.randomUUID(); UUID session = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        PlanRevisionQueryPort revisions = mock(PlanRevisionQueryPort.class);
        SessionSafetyDecisionQueryPort safety = mock(SessionSafetyDecisionQueryPort.class);
        BarrierReportRepository reports = mock(BarrierReportRepository.class);
        AdherenceSpecialistSignalPort signals = mock(AdherenceSpecialistSignalPort.class);
        when(accounts.requireActive("participant")).thenReturn(new CurrentAccount(participant, "participant", ProfileType.PARTICIPANT));
        var snapshot = snapshot(participant, revision, session);
        when(revisions.findActiveRevision(participant)).thenReturn(Optional.of(snapshot));
        when(revisions.findRevision(revision)).thenReturn(Optional.of(snapshot));
        when(safety.evaluateForSessions(eq(participant), eq(revision), any(), any())).thenReturn(Map.of(session,
                new SessionSafetyDecisionQueryPort.SessionSafetyDecision(session,
                        SessionSafetyDecisionQueryPort.SafetyDecisionStatus.ALLOWED, UUID.randomUUID(), List.of(), Instant.EPOCH)));
        AdherenceMetricsService metrics = mock(AdherenceMetricsService.class);
        BarrierReportService service = new BarrierReportService(accounts, revisions,
                mock(SessionExecutionAttemptQueryPort.class), safety, reports, signals, mock(RecoveryEpisodeService.class),
                metrics, mock(AuditRecorder.class), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        var view = service.report("participant", new BarrierReportController.BarrierReportCommand(session, null,
                "NO_TIME", null), "no-time-1");

        assertThat(view.proposedOptions()).containsExactly("START_SHORT", "START_MINIMUM", "RESCHEDULE", "CONTACT_SPECIALIST");
        assertThat(view.actionOutcome()).isEqualTo("OPTIONS_PRESENTED");
        verify(reports).saveAndFlush(any(BarrierReport.class));
        verify(metrics).record(eq(participant), eq("BARRIER_REPORTED"), any(), eq(revision), eq(session),
                org.mockito.ArgumentMatchers.isNull(), eq("BARRIER_RESPONSE_V1"), org.mockito.ArgumentMatchers.isNull());
        verify(signals, never()).signalContact(any(), any(), any(), anyBoolean());
    }

    private static PlanRevisionQueryPort.PlanRevisionSnapshot snapshot(UUID participant, UUID revision, UUID session) {
        var variant = new PlanRevisionQueryPort.SessionVariantSnapshot(UUID.randomUUID(), "SHORT", null, List.of());
        var minimum = new PlanRevisionQueryPort.SessionVariantSnapshot(UUID.randomUUID(), "MINIMUM", null, List.of());
        var sessionSnapshot = new PlanRevisionQueryPort.SessionSnapshot(session, "Session", null, null, null,
                20, "ASSIGNED", List.of(), List.of(variant, minimum));
        var microcycle = new PlanRevisionQueryPort.MicrocycleSnapshot(UUID.randomUUID(), 1, "M", null, null, null, null, List.of(sessionSnapshot));
        var cycle = new PlanRevisionQueryPort.CycleSnapshot(UUID.randomUUID(), 1, "C", null, null, null, null, List.of(microcycle));
        return new PlanRevisionQueryPort.PlanRevisionSnapshot(revision, UUID.randomUUID(), participant, 1,
                null, 0, "ACTIVE", UUID.randomUUID(), "AUTHOR", Instant.EPOCH, null, "PASS", null,
                null, null, List.of(), List.of(cycle), List.of());
    }
}
