package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.calendar.Appointment;
import com.motionecosystem.calendar.AppointmentService;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.calendar.api.SpecialistAppointmentEventQueryPort;
import com.motionecosystem.calendar.api.SpecialistOverdueAppointmentQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialistCalendarParticipantIdTest {

    private static final Instant NOW = Instant.parse("2030-06-10T10:00:00Z");

    @Test
    void todayShowsAnAppointmentForAnAccountFreeParticipantRecord() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistProfileService profiles = mock(SpecialistProfileService.class);
        SpecialistRelationshipService relationships = mock(SpecialistRelationshipService.class);
        ParticipantClientPort participants = mock(ParticipantClientPort.class);
        AppointmentService appointments = mock(AppointmentService.class);
        SpecialistOverdueAppointmentQueryPort overdueAppointments = mock(SpecialistOverdueAppointmentQueryPort.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(profiles.find(specialistId)).thenReturn(Optional.of(new SpecialistProfileService.ProfileView(
                specialistId, "Specialist", SpecialistKind.TRAINER, "UTC")));
        when(relationships.activeParticipantIds(specialistId)).thenReturn(Set.of(participantId));
        when(participants.find(participantId)).thenReturn(Optional.of(new ParticipantClientPort.ClientRecord(
                participantId, "Account-free client", ParticipantClientPort.RelationshipContext.CLIENT,
                ParticipantClientPort.RecordStatus.ACTIVE, 0)));
        AppointmentService.AppointmentView appointment = new AppointmentService.AppointmentView(UUID.randomUUID(), participantId,
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), Appointment.Type.CONSULTATION, Appointment.Status.SCHEDULED,
                Appointment.LocationMode.REMOTE, null, "Check-in", false, false, List.of("OPEN_APPOINTMENT"), 0);
        when(appointments.inRange(eq(specialistId), any(), any(), eq(Set.of(participantId)), eq(NOW))).thenReturn(List.of(appointment));
        UUID eventId = UUID.randomUUID();
        when(overdueAppointments.overdueOutcomeAppointments(specialistId, Set.of(participantId), NOW)).thenReturn(List.of(
                new SpecialistOverdueAppointmentQueryPort.OverdueAppointment(UUID.randomUUID(), participantId, NOW.minusSeconds(1),
                        "CONSULTATION", "CONFIRMED", eventId)));
        RecurringAvailabilityService availability = mock(RecurringAvailabilityService.class);
        when(availability.list(specialistId)).thenReturn(List.of());
        SpecialistTodayService service = new SpecialistTodayService(accounts, relationships, profiles, participants,
                availability, appointments, overdueAppointments, mock(SpecialistWorklistService.class), audit,
                Clock.fixed(NOW, ZoneOffset.UTC));

        SpecialistTodayService.TodayView view = service.today("specialist", LocalDate.of(2030, 6, 10));

        assertThat(view.appointments()).singleElement().satisfies(item -> {
            assertThat(item.participantId()).isEqualTo(participantId);
            assertThat(item.participantLabel()).isEqualTo("Account-free client");
        });
        assertThat(view.operationalTasks()).singleElement().satisfies(task -> {
            assertThat(task.type()).isEqualTo("APPOINTMENT_OUTCOME_REQUIRED");
            assertThat(task.title()).isEqualTo("Uzupełnij wynik spotkania z Account-free client");
            assertThat(task.navigationReference()).isEqualTo("/specialist/clients/" + participantId + "?eventId=appointment-event:" + eventId);
        });
        assertThat(view.counts().operationalTasks()).isOne();
    }

    @Test
    void timelineUsesTheCanonicalParticipantIdForAnAccountFreeAppointment() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistProfileService profiles = mock(SpecialistProfileService.class);
        SpecialistAuthorizationPort authorization = mock(SpecialistAuthorizationPort.class);
        SpecialistAppointmentQueryPort appointments = mock(SpecialistAppointmentQueryPort.class);
        SpecialistAppointmentEventQueryPort appointmentEvents = mock(SpecialistAppointmentEventQueryPort.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(profiles.find(specialistId)).thenReturn(Optional.of(new SpecialistProfileService.ProfileView(
                specialistId, "Specialist", SpecialistKind.TRAINER, "UTC")));
        when(authorization.requireCapabilities(eq(specialistId), eq(participantId), any(), any(), any())).thenReturn(
                new SpecialistAuthorizationPort.AuthorizationDecision(specialistId, participantId,
                        SpecialistAuthorizationPort.ProfessionalRole.TRAINER,
                        SpecialistAuthorizationPort.Purpose.PERFORMANCE_PLANNING,
                        Set.of(SpecialistAuthorizationPort.Capability.PLAN_PERFORMANCE)));
        when(appointmentEvents.timeline(eq(specialistId), eq(participantId), any(), any(), eq(null), eq(11))).thenReturn(List.of(
                new SpecialistAppointmentEventQueryPort.AppointmentEventSummary(UUID.randomUUID(), UUID.randomUUID(), "CREATED", null,
                        "SCHEDULED", NOW, NOW, "CONSULTATION", "Check-in")));
        SpecialistParticipantReadService service = new SpecialistParticipantReadService(accounts, profiles, authorization,
                mock(ParticipantClientPort.class), mock(com.motionecosystem.participant.api.ParticipantContextQueryPort.class),
                appointments, appointmentEvents, mock(PlanRevisionQueryPort.class), mock(ParticipantExecutionHistoryQueryPort.class),
                mock(ParticipantSpecialistRelationshipRepository.class), mock(SpecialistWorklistService.class), mock(AuditRecorder.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var view = service.timeline("specialist", participantId, new SpecialistParticipantReadService.TimelineQuery(
                NOW.minusSeconds(3600), NOW.plusSeconds(7200), Set.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT),
                SpecialistParticipantReadService.Granularity.DETAIL, null, 10));

        assertThat(view.items()).singleElement().extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::category)
                .isEqualTo("APPOINTMENT");
        verify(appointmentEvents).timeline(eq(specialistId), eq(participantId), any(), any(), eq(null), eq(11));
    }
}
