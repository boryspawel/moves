package com.motionecosystem.participantgoals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.*;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
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
                mock(SpecialistRelationshipService.class), mock(SpecialistAuthorizationPort.class), mock(AuditRecorder.class), Clock.fixed(NOW, ZoneOffset.UTC));
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
        ParticipantGoalEventQueryAdapter adapter = new ParticipantGoalEventQueryAdapter(events);
        UUID participant = UUID.randomUUID();
        Instant from = Instant.parse("2030-01-01T00:00:00Z");
        Instant to = Instant.parse("2030-02-01T00:00:00Z");

        adapter.timeline(participant, from, to, null, 10);

        verify(events).timelineWithoutCursor(eq(participant), eq(from), eq(to), any(Pageable.class));
        verify(events, never()).timelineAfterCursor(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void timelineWithCursorUsesSeekQuery() {
        ParticipantGoalEventRepository events = mock(ParticipantGoalEventRepository.class);
        ParticipantGoalEventQueryAdapter adapter = new ParticipantGoalEventQueryAdapter(events);
        UUID participant = UUID.randomUUID();
        Instant from = Instant.parse("2030-01-01T00:00:00Z");
        Instant to = Instant.parse("2030-02-01T00:00:00Z");
        var cursor = new com.motionecosystem.participantgoals.api.ParticipantGoalEventQueryPort.SeekCursor(
                Instant.parse("2030-01-15T00:00:00Z"), Instant.parse("2030-01-16T00:00:00Z"), UUID.randomUUID());

        adapter.timeline(participant, from, to, cursor, 10);

        verify(events).timelineAfterCursor(eq(participant), eq(from), eq(to), eq(cursor.effectiveAt()), eq(cursor.recordedAt()),
                eq(cursor.eventId()), any(Pageable.class));
        verify(events, never()).timelineWithoutCursor(any(), any(), any(), any());
    }
}
