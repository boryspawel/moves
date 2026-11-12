package com.motionecosystem.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.calendar.api.SpecialistAppointmentHistoryQueryPort;
import com.motionecosystem.calendar.api.SpecialistAppointmentHistoryQueryPort.AppointmentSnapshot;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.participantdocumentation.api.ParticipantDocumentationEventQueryPort;
import com.motionecosystem.participantgoals.api.SpecialistParticipantGoalHistoryQueryPort;
import com.motionecosystem.participantmeasurements.api.SpecialistMeasurementHistoryQueryPort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import com.motionecosystem.trainingexecution.api.SpecialistExecutionHistoryQueryPort;
import com.motionecosystem.trainingplanning.api.SpecialistPlanningHistoryQueryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialistParticipantHistoryServiceTest {
    private static final Instant FROM = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2030-02-01T00:00:00Z");

    @Test
    void childFilterAdmitsWholeEncounterAndHydratesLateChildrenOutsideRange() {
        Fixture fixture = new Fixture();
        AppointmentSnapshot appointment = fixture.appointment(FROM.plusSeconds(60), "CANCELLED");
        var lateMeasurement = new SpecialistMeasurementHistoryQueryPort.MeasurementFact(UUID.randomUUID(), fixture.participant,
                appointment.appointmentId(), "WEIGHT", BigDecimal.TEN, "kg", "MANUAL", TO.plusSeconds(86_400), TO.plusSeconds(86_400), "APPOINTMENT_CLOSEOUT");
        when(fixture.appointments.appointmentCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(appointment));
        when(fixture.appointments.appointmentsByIds(any(), any(), any(), any())).thenReturn(List.of(appointment));
        when(fixture.measurements.appointmentIdsWithMeasurements(any(), any(), any(), any())).thenReturn(Set.of(appointment.appointmentId()));
        when(fixture.measurements.measurementsByAppointmentIds(any(), any(), any(), any())).thenReturn(List.of(lateMeasurement));

        var page = fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.MEASUREMENT), 10, null);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(SpecialistParticipantHistoryService.ItemKind.ENCOUNTER);
            assertThat(item.matchedKinds()).containsExactlyInAnyOrder(SpecialistParticipantHistoryService.FilterKind.APPOINTMENT,
                    SpecialistParticipantHistoryService.FilterKind.MEASUREMENT);
            assertThat(item.encounter().appointment().status()).isEqualTo("CANCELLED");
            assertThat(item.encounter().measurements()).extracting(SpecialistParticipantHistoryService.MeasurementView::id)
                    .containsExactly(lateMeasurement.id());
        });
    }

    @Test
    void unlinkedMeasurementAndSelfOrLegacyExecutionsRemainStandalone() {
        Fixture fixture = new Fixture();
        var nearbyAppointment = fixture.appointment(FROM.plusSeconds(2), "COMPLETED");
        var measurement = new SpecialistMeasurementHistoryQueryPort.MeasurementFact(UUID.randomUUID(), fixture.participant,
                UUID.randomUUID(), "WEIGHT", BigDecimal.ONE, "kg", "MANUAL", FROM.plusSeconds(3), FROM.plusSeconds(4), "SPECIALIST_MANUAL");
        UUID session = UUID.randomUUID();
        var linkedAppointment = new AppointmentSnapshot(UUID.randomUUID(), fixture.specialist, fixture.participant,
                FROM.plusSeconds(5), FROM.plusSeconds(65), "TRAINING", "COMPLETED", session);
        var self = new SpecialistExecutionHistoryQueryPort.ExecutionFact(UUID.randomUUID(), fixture.participant, session,
                null, "SELF", fixture.participant, "COMPLETE", FROM.plusSeconds(2), true, 1);
        var legacy = new SpecialistExecutionHistoryQueryPort.ExecutionFact(UUID.randomUUID(), fixture.participant, session,
                null, "LEGACY", fixture.specialist, "COMPLETE", FROM.plusSeconds(1), true, 1);
        when(fixture.measurements.measurementCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(measurement));
        when(fixture.executions.executionCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(self, legacy));
        when(fixture.appointments.appointmentCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(nearbyAppointment, linkedAppointment));
        when(fixture.appointments.appointmentsByPlannedSessionIds(any(), any(), any(), any())).thenReturn(List.of(linkedAppointment));
        when(fixture.executions.executionLinksByPlannedSessionIds(any(), any(), any(), any())).thenReturn(List.of(
                new SpecialistExecutionHistoryQueryPort.ExecutionLink(self.id(), fixture.participant, session, fixture.participant, "SELF"),
                new SpecialistExecutionHistoryQueryPort.ExecutionLink(legacy.id(), fixture.participant, session, fixture.specialist, "LEGACY")));

        var page = fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.MEASUREMENT,
                SpecialistParticipantHistoryService.FilterKind.EXECUTION), 10, null);

        assertThat(page.items()).allSatisfy(item -> assertThat(item.kind()).isEqualTo(SpecialistParticipantHistoryService.ItemKind.STANDALONE));
        assertThat(page.items()).extracting(SpecialistParticipantHistoryService.HistoryItem::stableItemId)
                .containsExactlyInAnyOrder("measurement:" + measurement.id(), "execution:" + self.id(), "execution:" + legacy.id());
    }

    @Test
    void hiddenChildDoesNotMatchButDerivedGoalObservationIsNotDuplicated() {
        Fixture fixture = new Fixture();
        AppointmentSnapshot appointment = fixture.appointment(FROM.plusSeconds(10), "NO_SHOW");
        when(fixture.appointments.appointmentCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(appointment));
        var derived = new SpecialistParticipantGoalHistoryQueryPort.GoalHistoryCandidate(UUID.randomUUID(), UUID.randomUUID(), null,
                "OBSERVATION_RECORDED", "ACTIVE", FROM.plusSeconds(20), FROM.plusSeconds(20), UUID.randomUUID());
        var independent = new SpecialistParticipantGoalHistoryQueryPort.GoalHistoryCandidate(UUID.randomUUID(), UUID.randomUUID(), null,
                "ACHIEVED", "ACHIEVED", FROM.plusSeconds(21), FROM.plusSeconds(21), null);
        when(fixture.goals.goalHistoryCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(derived, independent));

        var measurementOnly = fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.MEASUREMENT), 10, null);
        var goals = fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.GOAL), 10, null);

        assertThat(measurementOnly.items()).isEmpty();
        assertThat(goals.items()).extracting(SpecialistParticipantHistoryService.HistoryItem::stableItemId)
                .containsExactly("goal-event:" + independent.eventId());
    }

    @Test
    void pagesTopLevelEncountersStablyAndBindsCursorToQuery() {
        Fixture fixture = new Fixture();
        AppointmentSnapshot older = fixture.appointment(FROM.plusSeconds(10), "SCHEDULED");
        AppointmentSnapshot newer = new AppointmentSnapshot(UUID.randomUUID(), fixture.specialist, fixture.participant,
                FROM.plusSeconds(20), FROM.plusSeconds(80), "TRAINING", "SCHEDULED", null);
        when(fixture.appointments.appointmentCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(older, newer));
        when(fixture.appointments.appointmentsByIds(any(), any(), any(), any())).thenAnswer(invocation -> List.of(older, newer).stream()
                .filter(item -> invocation.<Set<UUID>>getArgument(3).contains(item.appointmentId())).toList());

        var first = fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.APPOINTMENT), 1, null);
        var second = fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.APPOINTMENT), 1, first.nextCursor());

        assertThat(first.items()).extracting(SpecialistParticipantHistoryService.HistoryItem::stableItemId)
                .containsExactly("encounter:" + newer.appointmentId());
        assertThat(second.items()).extracting(SpecialistParticipantHistoryService.HistoryItem::stableItemId)
                .containsExactly("encounter:" + older.appointmentId());
        assertThatThrownBy(() -> fixture.service.history("specialist", fixture.participant, fixture.context,
                new SpecialistParticipantHistoryService.HistoryQuery(FROM.plusSeconds(1), TO,
                        Set.of(SpecialistParticipantHistoryService.FilterKind.APPOINTMENT), 1, first.nextCursor())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("history cursor does not match query");
    }

    @Test
    void preservesOwnerFailuresInsteadOfTreatingThemAsNoFacts() {
        Fixture fixture = new Fixture();
        when(fixture.appointments.appointmentCandidates(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("calendar unavailable"));

        assertThatThrownBy(() -> fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.APPOINTMENT), 10, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("calendar unavailable");
    }

    @Test
    void emptyFiltersIncludeCancelledNoShowAndFutureAppointmentCandidates() {
        Fixture fixture = new Fixture();
        AppointmentSnapshot cancelled = fixture.appointment(FROM.plusSeconds(10), "CANCELLED");
        AppointmentSnapshot noShow = fixture.appointment(FROM.plusSeconds(20), "NO_SHOW");
        AppointmentSnapshot future = fixture.appointment(FROM.plusSeconds(30), "SCHEDULED");
        when(fixture.appointments.appointmentCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(cancelled, noShow, future));
        when(fixture.appointments.appointmentsByIds(any(), any(), any(), any())).thenAnswer(invocation -> List.of(cancelled, noShow, future).stream()
                .filter(item -> invocation.<Set<UUID>>getArgument(3).contains(item.appointmentId())).toList());

        var page = fixture.history(Set.of(), 10, null);

        assertThat(page.items()).extracting(item -> item.encounter().appointment().status())
                .containsExactly("SCHEDULED", "NO_SHOW", "CANCELLED");
    }

    @Test
    void ambiguousTwoAppointmentSessionKeepsEvenSpecialistExecutionStandalone() {
        Fixture fixture = new Fixture();
        UUID session = UUID.randomUUID();
        AppointmentSnapshot first = new AppointmentSnapshot(UUID.randomUUID(), fixture.specialist, fixture.participant,
                FROM.plusSeconds(10), FROM.plusSeconds(70), "TRAINING", "COMPLETED", session);
        AppointmentSnapshot second = new AppointmentSnapshot(UUID.randomUUID(), fixture.specialist, fixture.participant,
                FROM.plusSeconds(20), FROM.plusSeconds(80), "TRAINING", "COMPLETED", session);
        var execution = new SpecialistExecutionHistoryQueryPort.ExecutionFact(UUID.randomUUID(), fixture.participant, session,
                null, "SPECIALIST", fixture.specialist, "COMPLETE", FROM.plusSeconds(30), true, 1);
        when(fixture.executions.executionCandidates(any(), any(), any(), any(), any())).thenReturn(List.of(execution));
        when(fixture.executions.executionLinksByPlannedSessionIds(any(), any(), any(), any())).thenReturn(List.of(
                new SpecialistExecutionHistoryQueryPort.ExecutionLink(execution.id(), fixture.participant, session, fixture.specialist, "SPECIALIST")));
        when(fixture.appointments.appointmentsByPlannedSessionIds(any(), any(), any(), any())).thenReturn(List.of(first, second));

        var page = fixture.history(Set.of(SpecialistParticipantHistoryService.FilterKind.EXECUTION), 10, null);

        assertThat(page.items()).extracting(SpecialistParticipantHistoryService.HistoryItem::stableItemId)
                .containsExactly("execution:" + execution.id());
    }

    @Test
    void resolvesLegacyAppointmentEventToFullyHydratedEncounterOutsideTheCurrentRange() {
        Fixture fixture = new Fixture();
        AppointmentSnapshot appointment = fixture.appointment(TO.plusSeconds(86_400), "COMPLETED");
        var lifecycle = new SpecialistAppointmentHistoryQueryPort.LifecycleSnapshot(UUID.randomUUID(), appointment.appointmentId(),
                TO.plusSeconds(86_400), TO.plusSeconds(86_400), "COMPLETED", "IN_PROGRESS", "COMPLETED");
        var measurement = new SpecialistMeasurementHistoryQueryPort.MeasurementFact(UUID.randomUUID(), fixture.participant,
                appointment.appointmentId(), "WEIGHT", BigDecimal.TEN, "kg", "MANUAL", TO.plusSeconds(86_400),
                TO.plusSeconds(86_400), "SPECIALIST_MANUAL");
        when(fixture.appointments.lifecycleByEventIds(any(), any(), any(), any())).thenReturn(List.of(lifecycle));
        when(fixture.appointments.appointmentsByIds(any(), any(), any(), any())).thenReturn(List.of(appointment));
        when(fixture.appointments.lifecycleByAppointmentIds(any(), any(), any(), any())).thenReturn(List.of(lifecycle));
        when(fixture.measurements.measurementsByAppointmentIds(any(), any(), any(), any())).thenReturn(List.of(measurement));

        var resolution = fixture.service.resolve("specialist", fixture.participant, fixture.context, "appointment-event:" + lifecycle.eventId());

        assertThat(resolution.item().stableItemId()).isEqualTo("encounter:" + appointment.appointmentId());
        assertThat(resolution.highlightKind()).isEqualTo(SpecialistParticipantHistoryService.HighlightKind.LIFECYCLE);
        assertThat(resolution.highlightId()).isEqualTo(lifecycle.eventId());
        assertThat(resolution.item().encounter().measurements()).extracting(SpecialistParticipantHistoryService.MeasurementView::id)
                .containsExactly(measurement.id());
    }

    @Test
    void resolvesLegacyProtectedDocumentationAndGoalToNeutralNavigationTargets() {
        Fixture fixture = new Fixture();
        UUID noteEvent = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID goalEvent = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        when(fixture.documentation.documentationByEventIds(any(), any(), any(), any())).thenReturn(List.of(
                new ParticipantDocumentationEventQueryPort.DocumentationHistoryCandidate(noteEvent, noteId, "NOTE", "CREATED", "FINAL",
                        "GENERAL", FROM, FROM, null)));
        when(fixture.goals.goalHistoryByEventIds(any(), any(), any(), any())).thenReturn(List.of(
                new SpecialistParticipantGoalHistoryQueryPort.GoalHistoryCandidate(goalEvent, goalId, null, "ACHIEVED", "ACHIEVED", FROM, FROM, null)));

        var note = fixture.service.resolve("specialist", fixture.participant, fixture.context, "participant-note-event:" + noteEvent);
        var goal = fixture.service.resolve("specialist", fixture.participant, fixture.context, "participant-goal-event:" + goalEvent);

        assertThat(note.item().stableItemId()).isEqualTo("note-event:" + noteEvent);
        assertThat(note.navigationTarget()).isEqualTo(new SpecialistParticipantHistoryService.NavigationTarget(
                SpecialistParticipantHistoryService.NavigationKind.DOCUMENTATION_NOTE, noteId));
        assertThat(goal.item().stableItemId()).isEqualTo("goal-event:" + goalEvent);
        assertThat(goal.navigationTarget()).isEqualTo(new SpecialistParticipantHistoryService.NavigationTarget(
                SpecialistParticipantHistoryService.NavigationKind.GOAL, goalId));
    }

    @Test
    void resolverReturns404ForUnknownAndDeniedParticipantContext() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.resolve("specialist", fixture.participant, fixture.context, "encounter:" + UUID.randomUUID()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("history item not found");
        when(fixture.workspace.requireParticipantCapabilities(any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> fixture.service.resolve("specialist", UUID.randomUUID(), fixture.context, "encounter:" + UUID.randomUUID()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("history item not found");
    }

    private static final class Fixture {
        final UUID specialist = UUID.randomUUID();
        final UUID participant = UUID.randomUUID();
        final ActingContext context = new ActingContext(ProfessionalRole.TRAINER);
        final CurrentAccountService accounts = mock(CurrentAccountService.class);
        final SpecialistWorkspacePort workspace = mock(SpecialistWorkspacePort.class);
        final SpecialistAppointmentHistoryQueryPort appointments = mock(SpecialistAppointmentHistoryQueryPort.class);
        final SpecialistPlanningHistoryQueryPort planning = mock(SpecialistPlanningHistoryQueryPort.class);
        final SpecialistExecutionHistoryQueryPort executions = mock(SpecialistExecutionHistoryQueryPort.class);
        final SpecialistMeasurementHistoryQueryPort measurements = mock(SpecialistMeasurementHistoryQueryPort.class);
        final ParticipantDocumentationEventQueryPort documentation = mock(ParticipantDocumentationEventQueryPort.class);
        final SpecialistParticipantGoalHistoryQueryPort goals = mock(SpecialistParticipantGoalHistoryQueryPort.class);
        final SpecialistParticipantHistoryService service;

        Fixture() {
            when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialist, "specialist", ProfileType.SPECIALIST));
            when(workspace.findProfile(specialist)).thenReturn(Optional.of(new SpecialistWorkspacePort.Profile(specialist,
                    SpecialistWorkspacePort.WorkspaceRole.TRAINER, "UTC")));
            when(workspace.requireParticipantCapabilities(any(), any(), any(), any(), any())).thenReturn(new SpecialistWorkspacePort.AuthorizationDecision(
                    SpecialistWorkspacePort.WorkspaceRole.TRAINER, SpecialistWorkspacePort.WorkspacePurpose.PERFORMANCE_PLANNING, Set.of()));
            service = new SpecialistParticipantHistoryService(accounts, workspace, appointments, planning, executions, measurements, documentation, goals);
        }

        AppointmentSnapshot appointment(Instant at, String status) {
            return new AppointmentSnapshot(UUID.randomUUID(), specialist, participant, at, at.plusSeconds(60), "TRAINING", status, null);
        }

        SpecialistParticipantHistoryService.HistoryPage history(Set<SpecialistParticipantHistoryService.FilterKind> types, int limit, String cursor) {
            return service.history("specialist", participant, context,
                    new SpecialistParticipantHistoryService.HistoryQuery(FROM, TO, types, limit, cursor));
        }
    }
}
