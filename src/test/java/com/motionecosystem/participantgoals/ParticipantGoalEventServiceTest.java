package com.motionecosystem.participantgoals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.*;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ParticipantGoalEventServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void createPersistsOneCurrentGoalSnapshotAndReplayPersistsNone() {
        UUID specialist = UUID.randomUUID(); UUID participant = UUID.randomUUID();
        ParticipantGoalRepository goals = mock(ParticipantGoalRepository.class);
        GoalOutcomeRepository outcomes = mock(GoalOutcomeRepository.class);
        GoalIdempotencyRepository idempotency = mock(GoalIdempotencyRepository.class);
        ParticipantGoalEventRepository events = mock(ParticipantGoalEventRepository.class);
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialist, "specialist", ProfileType.SPECIALIST));
        when(goals.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(outcomes.findByGoalIdOrderByPositionAsc(any())).thenReturn(List.of());
        ParticipantGoalService service = new ParticipantGoalService(goals, outcomes, idempotency, null, null, events, accounts,
                mock(SpecialistAuthorizationPort.class), mock(AuditRecorder.class), Clock.fixed(NOW, ZoneOffset.UTC));
        var command = new ParticipantGoalService.CreateParticipantGoalCommand(ParticipantGoal.Category.PERFORMANCE, "5 km", null, 50, null, List.of());

        ParticipantGoalService.ParticipantGoalView created = service.create("specialist", participant,
                new SpecialistAuthorizationPort.ActingContext(SpecialistAuthorizationPort.ProfessionalRole.TRAINER), "create", command);

        ArgumentCaptor<ParticipantGoalEvent> captured = ArgumentCaptor.forClass(ParticipantGoalEvent.class);
        verify(events).save(captured.capture());
        assertThat(captured.getValue()).extracting(event -> event.eventType, event -> event.fromStatus, event -> event.toStatus,
                event -> event.effectiveAt, event -> event.recordedAt, event -> event.goalId)
                .containsExactly(ParticipantGoalEvent.Type.CREATED, null, ParticipantGoal.Status.ACTIVE, NOW, NOW, created.id());
        ParticipantGoal replayGoal = new ParticipantGoal(participant, specialist, ParticipantGoal.Category.PERFORMANCE, "5 km", null, 50, null, NOW);
        when(idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(specialist, "CREATE:" + participant, "replay"))
                .thenReturn(Optional.of(new GoalIdempotency(specialist, "CREATE:" + participant, "replay", replayGoal.id, NOW)));
        when(goals.findById(replayGoal.id)).thenReturn(Optional.of(replayGoal));
        service.create("specialist", participant, new SpecialistAuthorizationPort.ActingContext(SpecialistAuthorizationPort.ProfessionalRole.TRAINER), "replay", command);
        verify(events, times(1)).save(any());
    }

    @Test
    void timelineWithoutCursorDoesNotBindCursorParameters() {
        ParticipantGoalEventRepository events = mock(ParticipantGoalEventRepository.class);
        UUID actor = UUID.randomUUID();
        ParticipantGoalEventQueryAdapter adapter = eventAdapter(events, actor);
        UUID participant = UUID.randomUUID();
        Instant from = Instant.parse("2030-01-01T00:00:00Z");
        Instant to = Instant.parse("2030-02-01T00:00:00Z");

        adapter.timeline("specialist", participant, trainer(), from, to, null, 10);

        verify(events).timelineWithoutCursor(eq(actor), eq(participant), eq(from), eq(to), any(Pageable.class));
        verify(events, never()).timelineAfterCursor(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void timelineWithCursorUsesSeekQuery() {
        ParticipantGoalEventRepository events = mock(ParticipantGoalEventRepository.class);
        UUID actor = UUID.randomUUID();
        ParticipantGoalEventQueryAdapter adapter = eventAdapter(events, actor);
        UUID participant = UUID.randomUUID();
        Instant from = Instant.parse("2030-01-01T00:00:00Z");
        Instant to = Instant.parse("2030-02-01T00:00:00Z");
        var cursor = new com.motionecosystem.participantgoals.api.ParticipantGoalEventQueryPort.SeekCursor(
                Instant.parse("2030-01-15T00:00:00Z"), Instant.parse("2030-01-16T00:00:00Z"), UUID.randomUUID());

        adapter.timeline("specialist", participant, trainer(), from, to, cursor, 10);

        verify(events).timelineAfterCursor(eq(actor), eq(participant), eq(from), eq(to), eq(cursor.effectiveAt()), eq(cursor.recordedAt()),
                eq(cursor.eventId()), any(Pageable.class));
        verify(events, never()).timelineWithoutCursor(any(), any(), any(), any(), any());
    }

    @Test
    void goalTimelineHidesRecordedMeasurementWithoutMeasurementConsentButKeepsPlanEvents() {
        UUID actor = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        Instant at = Instant.parse("2030-01-15T00:00:00Z");
        ParticipantGoalEventRepository events = mock(ParticipantGoalEventRepository.class);
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistAuthorizationPort authorization = mock(SpecialistAuthorizationPort.class);
        when(accounts.requireActive("specialist"))
                .thenReturn(new CurrentAccount(actor, "specialist", ProfileType.SPECIALIST));
        ParticipantGoalEventQueryAdapter adapter = new ParticipantGoalEventQueryAdapter(events, accounts, authorization);

        ParticipantGoal goal = new ParticipantGoal(participant, actor, ParticipantGoal.Category.PERFORMANCE,
                "Goal", null, 50, null, at);
        GoalOutcome outcome = new GoalOutcome(goal.id, "body-weight", null, java.math.BigDecimal.TEN,
                "kg", null, TargetComparator.AT_MOST, 0, at);
        GoalObservation measurement = new GoalObservation(goal.id, outcome.id, participant, java.math.BigDecimal.ONE,
                "kg", "manual", at, actor, at, UUID.randomUUID());
        ParticipantGoalEvent recorded = new ParticipantGoalEvent(ParticipantGoalEvent.Type.OBSERVATION_RECORDED,
                goal, goal.status, at, at, measurement, outcome, null);
        ParticipantGoalEvent created = new ParticipantGoalEvent(ParticipantGoalEvent.Type.CREATED,
                goal, null, at, at, null, null, null);
        when(events.timelineWithoutCursor(eq(actor), eq(participant), eq(at.minusSeconds(1)), eq(at.plusSeconds(1)), any()))
                .thenReturn(List.of(recorded, created));
        when(events.findOwnedEvent(actor, participant, recorded.id)).thenReturn(Optional.of(recorded));

        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(authorization).requireCapabilities(eq(actor), eq(participant), eq(trainer()),
                        eq(Set.of(SpecialistAuthorizationPort.Capability.VIEW_MEASUREMENTS)), any());

        var timeline = adapter.timeline("specialist", participant, trainer(), at.minusSeconds(1), at.plusSeconds(1), null, 10);
        assertThat(timeline).extracting(item -> item.eventId()).containsExactly(created.id);
        assertThat(adapter.findByParticipantId("specialist", participant, trainer(), recorded.id)).isEmpty();
    }

    private static SpecialistAuthorizationPort.ActingContext trainer() {
        return new SpecialistAuthorizationPort.ActingContext(SpecialistAuthorizationPort.ProfessionalRole.TRAINER);
    }

    private static ParticipantGoalEventQueryAdapter eventAdapter(ParticipantGoalEventRepository events, UUID actor) {
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(actor, "specialist", ProfileType.SPECIALIST));
        return new ParticipantGoalEventQueryAdapter(events, accounts, mock(SpecialistAuthorizationPort.class));
    }

    @Test
    void historyCandidateExposesOnlyDerivedObservationMeasurementLinkage() {
        UUID specialist = UUID.randomUUID(); UUID participant = UUID.randomUUID(); Instant at = Instant.parse("2030-01-15T00:00:00Z");
        ParticipantGoalRepository goals = mock(ParticipantGoalRepository.class);
        GoalOutcomeRepository outcomes = mock(GoalOutcomeRepository.class);
        GoalIdempotencyRepository idempotency = mock(GoalIdempotencyRepository.class);
        GoalObservationRepository observations = mock(GoalObservationRepository.class);
        GoalObservationIdempotencyRepository observationIdempotency = mock(GoalObservationIdempotencyRepository.class);
        ParticipantGoalEventRepository events = mock(ParticipantGoalEventRepository.class);
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialist, "specialist", ProfileType.SPECIALIST));
        ParticipantGoal goal = new ParticipantGoal(participant, specialist, ParticipantGoal.Category.PERFORMANCE, "private title", "private body", 50, null, at);
        GoalOutcome outcome = new GoalOutcome(goal.id, "RANGE", null, java.math.BigDecimal.TEN, "deg", null, TargetComparator.AT_LEAST, 0, at);
        UUID measurementId = UUID.randomUUID();
        GoalObservation observation = new GoalObservation(goal.id, outcome.id, participant, java.math.BigDecimal.ONE, "deg", "manual", at, specialist, at, measurementId);
        ParticipantGoalEvent event = new ParticipantGoalEvent(ParticipantGoalEvent.Type.OBSERVATION_RECORDED, goal, ParticipantGoal.Status.ACTIVE, at, at, observation, outcome, null);
        when(events.findHistoryCandidates(specialist, participant, at.minusSeconds(1), at.plusSeconds(1))).thenReturn(List.of(event));
        when(observations.findAllById(any())).thenReturn(List.of(observation));
        ParticipantGoalService service = new ParticipantGoalService(goals, outcomes, idempotency, observations, observationIdempotency, events,
                accounts, mock(SpecialistAuthorizationPort.class), null, mock(AuditRecorder.class), Clock.fixed(at, ZoneOffset.UTC));

        var candidate = service.goalHistoryCandidates("specialist", participant,
                new SpecialistAuthorizationPort.ActingContext(SpecialistAuthorizationPort.ProfessionalRole.TRAINER), at.minusSeconds(1), at.plusSeconds(1));

        assertThat(candidate).singleElement().extracting(item -> item.eventId(), item -> item.goalId(), item -> item.outcomeId(), item -> item.sourceMeasurementId())
                .containsExactly(event.id, goal.id, outcome.id, measurementId);
    }
}
