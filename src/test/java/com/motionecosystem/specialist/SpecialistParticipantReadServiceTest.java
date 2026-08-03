package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
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
        SpecialistProfileService profiles = mock(SpecialistProfileService.class);
        SpecialistAuthorizationPort authorization = mock(SpecialistAuthorizationPort.class);
        ParticipantClientPort participants = mock(ParticipantClientPort.class);
        ParticipantContextQueryPort contexts = mock(ParticipantContextQueryPort.class);
        SpecialistAppointmentQueryPort appointments = mock(SpecialistAppointmentQueryPort.class);
        PlanRevisionQueryPort revisions = mock(PlanRevisionQueryPort.class);
        ParticipantExecutionHistoryQueryPort executionHistory = mock(ParticipantExecutionHistoryQueryPort.class);
        ParticipantSpecialistRelationshipRepository relationships = mock(ParticipantSpecialistRelationshipRepository.class);
        SpecialistWorklistService worklist = mock(SpecialistWorklistService.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        ParticipantSpecialistRelationship relationship = mock(ParticipantSpecialistRelationship.class);
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        var decision = new SpecialistAuthorizationPort.AuthorizationDecision(specialistId, participantId,
                SpecialistAuthorizationPort.ProfessionalRole.TRAINER, SpecialistAuthorizationPort.Purpose.PERFORMANCE_PLANNING, Set.of());

        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(profiles.find(specialistId)).thenReturn(Optional.of(new SpecialistProfileService.ProfileView(specialistId, "Specialist", SpecialistKind.TRAINER, "UTC")));
        when(authorization.requireCapabilities(any(), any(), any(), anySet(), any())).thenReturn(decision);
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
        when(worklist.forParticipant(any(), any(), any(), any())).thenReturn(List.of());
        when(relationships.findBySpecialistAccountIdAndParticipantId(specialistId, participantId)).thenReturn(Optional.of(relationship));
        when(relationship.status()).thenReturn(ParticipantSpecialistRelationship.Status.ACTIVE);

        var workspace = new SpecialistParticipantReadService(accounts, profiles, authorization, participants, contexts, appointments,
                revisions, executionHistory, relationships, worklist, audit, clock).workspace("specialist", participantId);

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
        assertThat(new SpecialistParticipantReadService(accounts, profiles, authorization, participants, contexts, appointments,
                revisions, executionHistory, relationships, worklist, audit, clock).workspace("specialist", participantId).nextAppointment())
                .extracting(SpecialistParticipantReadService.AppointmentView::appointmentId,
                        SpecialistParticipantReadService.AppointmentView::status)
                .containsExactly(futureConfirmed.appointmentId(), "CONFIRMED");

        when(appointments.findForParticipant(any(), any(), any(), any(), anyInt())).thenReturn(List.of(
                appointment(now.plusSeconds(7_200), now.plusSeconds(10_800), "SCHEDULED"), currentScheduled));
        assertThat(new SpecialistParticipantReadService(accounts, profiles, authorization, participants, contexts, appointments,
                revisions, executionHistory, relationships, worklist, audit, clock).workspace("specialist", participantId).nextAppointment())
                .extracting(SpecialistParticipantReadService.AppointmentView::appointmentId,
                        SpecialistParticipantReadService.AppointmentView::status)
                .containsExactly(currentScheduled.appointmentId(), "SCHEDULED");
    }

    private static SpecialistAppointmentQueryPort.AppointmentSummary appointment(Instant startsAt, Instant endsAt, String status) {
        return new SpecialistAppointmentQueryPort.AppointmentSummary(UUID.randomUUID(), startsAt, endsAt,
                "CONSULTATION", status, null, startsAt, endsAt);
    }
}
