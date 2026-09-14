package com.motionecosystem.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.calendar.api.SpecialistAppointmentEventQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialistParticipantReadServiceTest {

    @Test
    void workspaceIncludesParticipantHeaderAndSelectsEarliestEligibleNextAppointment() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistWorkspacePort specialistWorkspace = mock(SpecialistWorkspacePort.class);
        ParticipantClientPort participants = mock(ParticipantClientPort.class);
        ParticipantContextQueryPort contexts = mock(ParticipantContextQueryPort.class);
        SpecialistAppointmentQueryPort appointments = mock(SpecialistAppointmentQueryPort.class);
        SpecialistAppointmentEventQueryPort appointmentEvents = mock(SpecialistAppointmentEventQueryPort.class);
        PlanRevisionQueryPort revisions = mock(PlanRevisionQueryPort.class);
        ParticipantExecutionHistoryQueryPort executionHistory = mock(ParticipantExecutionHistoryQueryPort.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        var decision = new SpecialistWorkspacePort.AuthorizationDecision(SpecialistWorkspacePort.WorkspaceRole.TRAINER,
                SpecialistWorkspacePort.WorkspacePurpose.PERFORMANCE_PLANNING, Set.of());

        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(specialistWorkspace.findProfile(specialistId)).thenReturn(Optional.of(new SpecialistWorkspacePort.Profile(specialistId,
                SpecialistWorkspacePort.WorkspaceRole.TRAINER, "UTC")));
        when(specialistWorkspace.requireParticipantCapabilities(any(), any(), any(), any(), any())).thenReturn(decision);
        when(participants.find(participantId)).thenReturn(Optional.of(new ParticipantClientPort.ClientRecord(participantId, "Account-free participant",
                ParticipantClientPort.RelationshipContext.CLIENT, ParticipantClientPort.RecordStatus.ACTIVE, 0)));
        when(contexts.findContext(participantId)).thenReturn(Optional.empty());
        Instant now = clock.instant();
        SpecialistAppointmentQueryPort.AppointmentSummary currentInProgress = appointment(now.minusSeconds(3_600), now.plusSeconds(1_800), "IN_PROGRESS");
        SpecialistAppointmentQueryPort.AppointmentSummary currentScheduled = appointment(now.minusSeconds(1_800), now.plusSeconds(1_800), "SCHEDULED");
        SpecialistAppointmentQueryPort.AppointmentSummary futureConfirmed = appointment(now.plusSeconds(10_800), now.plusSeconds(14_400), "CONFIRMED");
        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                appointment(now.minusSeconds(7_200), now.minusSeconds(1), "SCHEDULED"),
                appointment(now.minusSeconds(7_200), now.plusSeconds(1_800), "CONFIRMED"),
                currentScheduled,
                currentInProgress,
                appointment(now.plusSeconds(3_600), now.plusSeconds(7_200), "CANCELLED"),
                appointment(now.plusSeconds(5_400), now.plusSeconds(7_200), "NO_SHOW"),
                appointment(now.plusSeconds(7_200), now.plusSeconds(10_800), "COMPLETED"),
                futureConfirmed,
                appointment(now.plusSeconds(14_400), now.plusSeconds(18_000), "SCHEDULED")));
        when(revisions.findActiveRevision(participantId)).thenReturn(Optional.empty());
        when(executionHistory.starts(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(specialistWorkspace.listParticipantWorklist(any(), any(), any(), any())).thenReturn(List.of());
        when(specialistWorkspace.findRelationship(specialistId, participantId)).thenReturn(Optional.of(
                new SpecialistWorkspacePort.Relationship("ACTIVE", Instant.parse("2030-01-01T00:00:00Z"))));

        var workspace = new SpecialistParticipantReadService(accounts, specialistWorkspace, participants, contexts, appointments, appointmentEvents,
                revisions, executionHistory, null, null, audit, clock).workspace("specialist", participantId);

        assertThat(workspace.participant())
                .extracting(
                        SpecialistParticipantReadService.ParticipantHeader::participantId,
                        SpecialistParticipantReadService.ParticipantHeader::displayName,
                        SpecialistParticipantReadService.ParticipantHeader::availableActions)
                .containsExactly(participantId, "Account-free participant", List.of("OPEN_WORKSPACE", "OPEN_TIMELINE"));
        assertThat(workspace.nextAppointment()).isNotNull()
                .extracting(SpecialistParticipantReadService.AppointmentView::appointmentId,
                        SpecialistParticipantReadService.AppointmentView::status)
                .containsExactly(currentInProgress.appointmentId(), "IN_PROGRESS");

        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                appointment(now.plusSeconds(14_400), now.plusSeconds(18_000), "SCHEDULED"), futureConfirmed));
        assertThat(new SpecialistParticipantReadService(accounts, specialistWorkspace, participants, contexts, appointments, appointmentEvents,
                revisions, executionHistory, null, null, audit, clock).workspace("specialist", participantId).nextAppointment())
                .extracting(SpecialistParticipantReadService.AppointmentView::appointmentId,
                        SpecialistParticipantReadService.AppointmentView::status)
                .containsExactly(futureConfirmed.appointmentId(), "CONFIRMED");

        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                appointment(now.plusSeconds(7_200), now.plusSeconds(10_800), "SCHEDULED"), currentScheduled));
        assertThat(new SpecialistParticipantReadService(accounts, specialistWorkspace, participants, contexts, appointments, appointmentEvents,
                revisions, executionHistory, null, null, audit, clock).workspace("specialist", participantId).nextAppointment())
                .extracting(SpecialistParticipantReadService.AppointmentView::appointmentId,
                        SpecialistParticipantReadService.AppointmentView::status)
                .containsExactly(currentScheduled.appointmentId(), "SCHEDULED");
    }

    @Test
    void resolvesOnlyScopedAppointmentPublicIdsAndMapsBaselineLikeTimeline() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistWorkspacePort specialistWorkspace = mock(SpecialistWorkspacePort.class);
        SpecialistAppointmentEventQueryPort appointmentEvents = mock(SpecialistAppointmentEventQueryPort.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        var decision = new SpecialistWorkspacePort.AuthorizationDecision(SpecialistWorkspacePort.WorkspaceRole.TRAINER,
                SpecialistWorkspacePort.WorkspacePurpose.PERFORMANCE_PLANNING, Set.of());
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(specialistWorkspace.findProfile(specialistId)).thenReturn(Optional.of(new SpecialistWorkspacePort.Profile(specialistId,
                SpecialistWorkspacePort.WorkspaceRole.TRAINER, "UTC")));
        when(specialistWorkspace.requireParticipantCapabilities(any(), any(), any(), any(), any())).thenReturn(decision);
        when(appointmentEvents.findBySpecialistAndParticipant(specialistId, participantId, eventId)).thenReturn(Optional.of(
                new SpecialistAppointmentEventQueryPort.AppointmentEventSummary(eventId, UUID.randomUUID(), "BASELINE", null, "COMPLETED",
                        Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T10:01:00Z"), "CONSULTATION", "Baseline")));
        SpecialistParticipantReadService service = new SpecialistParticipantReadService(accounts, specialistWorkspace,
                mock(ParticipantClientPort.class), mock(ParticipantContextQueryPort.class), mock(SpecialistAppointmentQueryPort.class), appointmentEvents,
                mock(PlanRevisionQueryPort.class), mock(ParticipantExecutionHistoryQueryPort.class), null, null, audit, Clock.systemUTC());

        var event = service.timelineEvent("specialist", participantId, "appointment-event:" + eventId);

        assertThat(event).extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::eventId,
                SpecialistParticipantReadService.ParticipantTimelineEvent::eventType,
                SpecialistParticipantReadService.ParticipantTimelineEvent::category)
                .containsExactly("appointment-event:" + eventId, "APPOINTMENT_COMPLETED", "APPOINTMENT");
        verify(appointmentEvents).findBySpecialistAndParticipant(specialistId, participantId, eventId);
        assertThatThrownBy(() -> service.timelineEvent("specialist", participantId, "session-execution:" + UUID.randomUUID()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    private static SpecialistAppointmentQueryPort.AppointmentSummary appointment(Instant startsAt, Instant endsAt, String status) {
        return new SpecialistAppointmentQueryPort.AppointmentSummary(UUID.randomUUID(), startsAt, endsAt,
                "CONSULTATION", status, null, startsAt, endsAt);
    }
}
