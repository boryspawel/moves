package com.motionecosystem.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.motionecosystem.adherence.api.AdherenceSummaryQueryPort;
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
import com.motionecosystem.trainingplanning.api.AppointmentSessionLinkPort;
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
    void workspaceUsesOnlyAuthorizedLinkedSessionContextAndBackendStartAvailability() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID otherParticipantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistWorkspacePort workspace = mock(SpecialistWorkspacePort.class);
        ParticipantClientPort participants = mock(ParticipantClientPort.class);
        ParticipantContextQueryPort contexts = mock(ParticipantContextQueryPort.class);
        SpecialistAppointmentQueryPort appointments = mock(SpecialistAppointmentQueryPort.class);
        SpecialistAppointmentEventQueryPort appointmentEvents = mock(SpecialistAppointmentEventQueryPort.class);
        PlanRevisionQueryPort revisions = mock(PlanRevisionQueryPort.class);
        AppointmentSessionLinkPort linkedSessions = mock(AppointmentSessionLinkPort.class);
        ParticipantExecutionHistoryQueryPort executions = mock(ParticipantExecutionHistoryQueryPort.class);
        SpecialistAppointmentExecutionService appointmentExecution = mock(SpecialistAppointmentExecutionService.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        var decision = new SpecialistWorkspacePort.AuthorizationDecision(SpecialistWorkspacePort.WorkspaceRole.TRAINER,
                SpecialistWorkspacePort.WorkspacePurpose.PERFORMANCE_PLANNING, Set.of());
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(workspace.findProfile(specialistId)).thenReturn(Optional.of(new SpecialistWorkspacePort.Profile(specialistId,
                SpecialistWorkspacePort.WorkspaceRole.TRAINER, "UTC")));
        when(workspace.requireParticipantCapabilities(any(), any(), any(), any(), any())).thenReturn(decision);
        when(participants.find(participantId)).thenReturn(Optional.of(new ParticipantClientPort.ClientRecord(participantId, "Participant",
                ParticipantClientPort.RelationshipContext.CLIENT, ParticipantClientPort.RecordStatus.ACTIVE, 0)));
        when(contexts.findContext(participantId)).thenReturn(Optional.empty());
        when(workspace.findRelationship(specialistId, participantId)).thenReturn(Optional.of(
                new SpecialistWorkspacePort.Relationship("ACTIVE", now.minusSeconds(60))));
        when(workspace.listParticipantWorklist(any(), any(), any(), any())).thenReturn(List.of());
        when(revisions.findActiveRevision(participantId)).thenReturn(Optional.empty());
        when(executions.starts(any(), any(), any(), anyInt())).thenReturn(List.of());
        var startable = new SpecialistAppointmentQueryPort.AppointmentSummary(UUID.randomUUID(), now.plusSeconds(60), now.plusSeconds(3_600),
                "TRAINING", "SCHEDULED", "Appointment context", sessionId, List.of("OPEN_APPOINTMENT", "START"), 4L, now, now);
        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(startable));
        when(linkedSessions.findAuthorizedContext("specialist", participantId, sessionId)).thenReturn(Optional.of(
                new AppointmentSessionLinkPort.AppointmentSessionContext(sessionId, "Linked session", planId, revisionId)));
        SpecialistParticipantReadService service = new SpecialistParticipantReadService(accounts, workspace, participants, contexts,
                appointments, appointmentEvents, revisions, linkedSessions, executions, null, null, null, audit, Clock.fixed(now, ZoneOffset.UTC),
                null, appointmentExecution);

        var view = service.workspace("specialist", participantId);

        assertThat(view.focus()).extracting(SpecialistParticipantReadService.OperationalFocusView::kind,
                SpecialistParticipantReadService.OperationalFocusView::primaryAction)
                .containsExactly(SpecialistParticipantReadService.FocusKind.NEXT_APPOINTMENT, "START_APPOINTMENT");
        assertThat(view.nextAppointment()).extracting(SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::plannedSessionId,
                SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::plannedSessionTitle,
                SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::planId,
                SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::revisionId)
                .containsExactly(sessionId, "Linked session", planId, revisionId);

        when(linkedSessions.findAuthorizedContext("specialist", participantId, sessionId)).thenReturn(Optional.empty());
        var redacted = service.workspace("specialist", participantId).nextAppointment();
        assertThat(redacted).extracting(SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::plannedSessionId,
                SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::plannedSessionTitle,
                SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::planId,
                SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::revisionId)
                .containsExactly(sessionId, null, null, null);
        verify(linkedSessions, times(2)).findAuthorizedContext("specialist", participantId, sessionId);

        var inProgress = new SpecialistAppointmentQueryPort.AppointmentSummary(UUID.randomUUID(), now.minusSeconds(600), now.plusSeconds(600),
                "TRAINING", "IN_PROGRESS", "Trwająca sesja", sessionId, List.of("COMPLETE"), 4L, now, now);
        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(inProgress));
        when(appointmentExecution.isExecutionRecorded("specialist", inProgress.appointmentId())).thenReturn(false);
        assertThat(service.workspace("specialist", participantId).focus())
                .extracting(SpecialistParticipantReadService.OperationalFocusView::primaryAction,
                        SpecialistParticipantReadService.OperationalFocusView::navigationTarget)
                .containsExactly("RECORD_SESSION_EXECUTION", "APPOINTMENT_EXECUTION");
        when(appointmentExecution.isExecutionRecorded("specialist", inProgress.appointmentId())).thenReturn(true);
        assertThat(service.workspace("specialist", participantId).focus())
                .extracting(SpecialistParticipantReadService.OperationalFocusView::primaryAction,
                        SpecialistParticipantReadService.OperationalFocusView::navigationTarget)
                .containsExactly("CONTINUE_CLOSEOUT", "APPOINTMENT_CLOSEOUT");
    }

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
        SpecialistAppointmentQueryPort.AppointmentSummary currentInProgress = appointment(now.minusSeconds(3_600), now.minusSeconds(1), "IN_PROGRESS");
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

        var service = new SpecialistParticipantReadService(accounts, specialistWorkspace, participants, contexts, appointments, appointmentEvents,
                revisions, executionHistory, null, null, audit, clock);
        var workspace = service.workspace("specialist", participantId);

        assertThat(workspace.participant())
                .extracting(
                        SpecialistParticipantReadService.ParticipantHeader::participantId,
                        SpecialistParticipantReadService.ParticipantHeader::displayName,
                        SpecialistParticipantReadService.ParticipantHeader::availableActions)
                .containsExactly(participantId, "Account-free participant", List.of("OPEN_WORKSPACE", "OPEN_TIMELINE"));
        assertThat(workspace.nextAppointment()).isNotNull()
                .extracting(SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::appointmentId,
                        SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::status)
                .containsExactly(currentInProgress.appointmentId(), "IN_PROGRESS");
        assertThat(workspace.focus())
                .extracting(SpecialistParticipantReadService.OperationalFocusView::kind,
                        SpecialistParticipantReadService.OperationalFocusView::appointmentId,
                        SpecialistParticipantReadService.OperationalFocusView::primaryAction)
                .containsExactly(SpecialistParticipantReadService.FocusKind.IN_PROGRESS_APPOINTMENT,
                        currentInProgress.appointmentId(), "CONTINUE_CLOSEOUT");
        UUID attentionId = UUID.randomUUID();
        when(specialistWorkspace.listParticipantWorklist(any(), any(), any(), any())).thenReturn(List.of(
                new SpecialistWorkspacePort.WorklistItem(attentionId, participantId, "ESCALATING_SYMPTOMS", "HIGH",
                        "Objawy wymagają sprawdzenia", "OPEN", now.minusSeconds(60), null)));
        assertThat(service.workspace("specialist", participantId).focus())
                .extracting(SpecialistParticipantReadService.OperationalFocusView::kind,
                        SpecialistParticipantReadService.OperationalFocusView::attentionId)
                .containsExactly(SpecialistParticipantReadService.FocusKind.IMPORTANT_ATTENTION, attentionId);

        UUID followUpId = UUID.randomUUID();
        when(specialistWorkspace.listParticipantWorklist(any(), any(), any(), any())).thenReturn(List.of(
                new SpecialistWorkspacePort.WorklistItem(followUpId, participantId, "POST_24H_FOLLOW_UP", "MEDIUM",
                        "Potwierdź dalsze kroki", "OPEN", now.minusSeconds(60), null)));
        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(futureConfirmed));
        assertThat(service.workspace("specialist", participantId).focus().kind())
                .isEqualTo(SpecialistParticipantReadService.FocusKind.NEXT_APPOINTMENT);
        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        assertThat(service.workspace("specialist", participantId).focus())
                .extracting(SpecialistParticipantReadService.OperationalFocusView::kind,
                        SpecialistParticipantReadService.OperationalFocusView::attentionId)
                .containsExactly(SpecialistParticipantReadService.FocusKind.FOLLOW_UP, followUpId);
        UUID standardFollowUpId = UUID.randomUUID();
        when(specialistWorkspace.listParticipantWorklist(any(), any(), any(), any())).thenReturn(List.of(
                new SpecialistWorkspacePort.WorklistItem(UUID.randomUUID(), participantId, "REPEATED_BARRIERS", "LOW",
                        "Starsza sprawa", "OPEN", now.minusSeconds(120), null),
                new SpecialistWorkspacePort.WorklistItem(standardFollowUpId, participantId, "TECHNIQUE_UNCERTAINTY", "MEDIUM",
                        "Nowsza sprawa", "OPEN", now.minusSeconds(30), null)));
        assertThat(service.workspace("specialist", participantId).focus())
                .extracting(SpecialistParticipantReadService.OperationalFocusView::kind,
                        SpecialistParticipantReadService.OperationalFocusView::attentionId)
                .containsExactly(SpecialistParticipantReadService.FocusKind.FOLLOW_UP, standardFollowUpId);
        when(specialistWorkspace.listParticipantWorklist(any(), any(), any(), any())).thenReturn(List.of(
                new SpecialistWorkspacePort.WorklistItem(followUpId, participantId, "POST_24H_FOLLOW_UP", "MEDIUM",
                        "Potwierdź dalsze kroki", "SNOOZED", now.minusSeconds(60), now.plusSeconds(60))));
        assertThat(service.workspace("specialist", participantId).focus().kind())
                .isEqualTo(SpecialistParticipantReadService.FocusKind.IDLE);

        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                appointment(now.plusSeconds(14_400), now.plusSeconds(18_000), "SCHEDULED"), futureConfirmed));
        assertThat(new SpecialistParticipantReadService(accounts, specialistWorkspace, participants, contexts, appointments, appointmentEvents,
                revisions, executionHistory, null, null, audit, clock).workspace("specialist", participantId).nextAppointment())
                .extracting(SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::appointmentId,
                        SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::status)
                .containsExactly(futureConfirmed.appointmentId(), "CONFIRMED");

        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                appointment(now.plusSeconds(7_200), now.plusSeconds(10_800), "SCHEDULED"), currentScheduled));
        assertThat(new SpecialistParticipantReadService(accounts, specialistWorkspace, participants, contexts, appointments, appointmentEvents,
                revisions, executionHistory, null, null, audit, clock).workspace("specialist", participantId).nextAppointment())
                .extracting(SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::appointmentId,
                        SpecialistParticipantReadService.ParticipantWorkspaceAppointmentView::status)
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

    @Test
    void directAdherenceSummaryDeniesSpecialistWithoutExecutionConsentCapability() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistWorkspacePort workspace = mock(SpecialistWorkspacePort.class);
        AdherenceSummaryQueryPort summaries = mock(AdherenceSummaryQueryPort.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(workspace.findProfile(specialistId)).thenReturn(Optional.of(new SpecialistWorkspacePort.Profile(specialistId,
                SpecialistWorkspacePort.WorkspaceRole.TRAINER, "UTC")));
        when(workspace.requireParticipantCapabilities(any(), any(), any(), any(), any()))
                .thenReturn(new SpecialistWorkspacePort.AuthorizationDecision(SpecialistWorkspacePort.WorkspaceRole.TRAINER,
                        SpecialistWorkspacePort.WorkspacePurpose.PERFORMANCE_PLANNING, Set.of("PLAN_PERFORMANCE")))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        SpecialistParticipantReadService service = new SpecialistParticipantReadService(accounts, workspace,
                mock(ParticipantClientPort.class), mock(ParticipantContextQueryPort.class), mock(SpecialistAppointmentQueryPort.class),
                mock(SpecialistAppointmentEventQueryPort.class), mock(PlanRevisionQueryPort.class),
                mock(ParticipantExecutionHistoryQueryPort.class), null, null, summaries, mock(AuditRecorder.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.adherenceSummary("specialist", participantId, null, null))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        verifyNoInteractions(summaries);
    }

    private static SpecialistAppointmentQueryPort.AppointmentSummary appointment(Instant startsAt, Instant endsAt, String status) {
        return new SpecialistAppointmentQueryPort.AppointmentSummary(UUID.randomUUID(), startsAt, endsAt,
                "CONSULTATION", status, null, startsAt, endsAt);
    }
}
