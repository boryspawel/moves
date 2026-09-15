package com.motionecosystem.adherence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.adherence.api.AdherenceSummary;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort.ExecutionState;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdherenceSummaryServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void summarizesOnlyCurrentActivePlanSessionsInLocalInclusivePeriod() {
        UUID participant = UUID.randomUUID();
        ParticipantContextQueryPort contexts = mock(ParticipantContextQueryPort.class);
        PlanRevisionQueryPort revisions = mock(PlanRevisionQueryPort.class);
        SessionExecutionProgressQueryPort progress = mock(SessionExecutionProgressQueryPort.class);
        when(contexts.findContextByParticipantId(participant)).thenReturn(Optional.of(
                new ParticipantContextQueryPort.ParticipantRecordContext(participant, "P", ZoneId.of("Europe/Warsaw"))));
        List<UUID> ids = java.util.stream.Stream.generate(UUID::randomUUID).limit(7).toList();
        UUID availabilityId = UUID.randomUUID();
        var scheduled = LocalDate.of(2026, 9, 15);
        var activeRevision = revision(participant, List.of(
                session(ids.get(0), scheduled), session(ids.get(1), scheduled), session(ids.get(2), scheduled),
                session(ids.get(3), scheduled), session(ids.get(4), scheduled), session(ids.get(5), scheduled),
                session(ids.get(6), scheduled),
                session(UUID.randomUUID(), LocalDate.of(2026, 8, 1)), availability(availabilityId, "2026-09-14T22:30:00Z"),
                availability(UUID.randomUUID(), null)));
        when(revisions.findActiveRevisions(participant)).thenReturn(List.of(activeRevision));
        when(progress.findForSessions(eq(participant), org.mockito.ArgumentMatchers.any())).thenReturn(Map.of(
                ids.get(0), state(ids.get(0), ExecutionState.NOT_STARTED), ids.get(1), state(ids.get(1), ExecutionState.IN_PROGRESS),
                ids.get(2), state(ids.get(2), ExecutionState.PAUSED), ids.get(3), state(ids.get(3), ExecutionState.COMPLETED),
                ids.get(4), state(ids.get(4), ExecutionState.PARTIAL), ids.get(5), state(ids.get(5), ExecutionState.SKIPPED),
                ids.get(6), state(ids.get(6), ExecutionState.STOPPED), availabilityId, state(availabilityId, ExecutionState.ABANDONED)));

        AdherenceSummary summary = new AdherenceSummaryService(contexts, revisions, progress, clock)
                .summarize(participant, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 15));

        assertThat(summary.scope()).isEqualTo("CURRENT_ACTIVE_PLANS");
        assertThat(summary).extracting(AdherenceSummary::dataStatus, AdherenceSummary::plannedSessions,
                AdherenceSummary::undatedSessionsExcluded, AdherenceSummary::notStartedSessions,
                AdherenceSummary::inProgressSessions, AdherenceSummary::pausedSessions, AdherenceSummary::completedSessions,
                AdherenceSummary::partialSessions, AdherenceSummary::skippedSessions, AdherenceSummary::stoppedSessions,
                AdherenceSummary::abandonedSessions, AdherenceSummary::completedPlannedPercent)
                .containsExactly("AVAILABLE", 8, 1, 1, 1, 1, 1, 1, 1, 1, 1, new java.math.BigDecimal("12.50"));
        verify(progress).findForSessions(eq(participant), org.mockito.ArgumentMatchers.argThat(value -> value.size() == 8));
    }

    @Test
    void distinguishesNoActivePlanAndMissingTimeZoneWithoutReadingExecution() {
        UUID participant = UUID.randomUUID();
        ParticipantContextQueryPort contexts = mock(ParticipantContextQueryPort.class);
        PlanRevisionQueryPort revisions = mock(PlanRevisionQueryPort.class);
        SessionExecutionProgressQueryPort progress = mock(SessionExecutionProgressQueryPort.class);
        when(contexts.findContextByParticipantId(participant)).thenReturn(Optional.of(
                new ParticipantContextQueryPort.ParticipantRecordContext(participant, "P", ZoneOffset.UTC)));
        when(revisions.findActiveRevisions(participant)).thenReturn(List.of());

        assertThat(new AdherenceSummaryService(contexts, revisions, progress, clock).summarize(participant, null, null).dataStatus())
                .isEqualTo("NO_ACTIVE_PLAN");
        verify(progress).findForSessions(eq(participant),
                org.mockito.ArgumentMatchers.<java.util.Collection<UUID>>argThat(java.util.Collection::isEmpty));

        when(contexts.findContextByParticipantId(participant)).thenReturn(Optional.empty());
        assertThat(new AdherenceSummaryService(contexts, revisions, progress, clock).summarize(participant, null, null).dataStatus())
                .isEqualTo("TIME_ZONE_REQUIRED");
    }

    private static SessionExecutionProgressQueryPort.SessionExecutionProgress state(UUID sessionId, ExecutionState state) {
        return new SessionExecutionProgressQueryPort.SessionExecutionProgress(sessionId, null, state,
                state == ExecutionState.COMPLETED || state == ExecutionState.PARTIAL || state == ExecutionState.SKIPPED || state == ExecutionState.STOPPED,
                Instant.EPOCH, null);
    }

    private static PlanRevisionQueryPort.PlanRevisionSnapshot revision(UUID participant, List<PlanRevisionQueryPort.SessionSnapshot> sessions) {
        var microcycle = new PlanRevisionQueryPort.MicrocycleSnapshot(UUID.randomUUID(), 1, "M", null, null, null, null, sessions);
        var cycle = new PlanRevisionQueryPort.CycleSnapshot(UUID.randomUUID(), 1, "C", null, null, null, null, List.of(microcycle));
        return new PlanRevisionQueryPort.PlanRevisionSnapshot(UUID.randomUUID(), UUID.randomUUID(), participant, 1,
                null, 0, "ACTIVE", UUID.randomUUID(), "AUTHOR", Instant.EPOCH, null, "PASS", null,
                null, null, List.of(), List.of(cycle), List.of());
    }

    private static PlanRevisionQueryPort.SessionSnapshot session(UUID id, LocalDate date) {
        return new PlanRevisionQueryPort.SessionSnapshot(id, "S", date, null, null, 10, "ASSIGNED", List.of());
    }

    private static PlanRevisionQueryPort.SessionSnapshot availability(UUID id, String availableFrom) {
        return new PlanRevisionQueryPort.SessionSnapshot(id, "S", null,
                availableFrom == null ? null : Instant.parse(availableFrom), null, 10, "ASSIGNED", List.of());
    }
}
