package com.motionecosystem.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.availability.api.AvailabilityCalendarPort;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.calendar.api.SpecialistOverdueAppointmentQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialistTodayServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Test
    void prioritizes_authoritative_early_in_progress_appointment_over_scheduled_appointments() {
        Fixture fixture = fixture();
        var scheduled = appointment(fixture.participantId, NOW.plusSeconds(10 * 60), NOW.plusSeconds(70 * 60), "SCHEDULED", false);
        var startedEarly = appointment(fixture.participantId, NOW.plusSeconds(20 * 60), NOW.plusSeconds(80 * 60), "IN_PROGRESS", true);
        when(fixture.appointments.inRange(eq(fixture.specialistId), any(), any(), eq(Set.of(fixture.participantId)), eq(NOW)))
                .thenReturn(List.of(scheduled, startedEarly));
        when(fixture.appointments.inProgress(fixture.specialistId, Set.of(fixture.participantId), NOW)).thenReturn(List.of(startedEarly));

        var view = fixture.service.today("specialist", LocalDate.of(2030, 6, 10));

        assertThat(view.currentAppointment()).extracting(SpecialistTodayService.AppointmentView::appointmentId,
                SpecialistTodayService.AppointmentView::status, SpecialistTodayService.AppointmentView::isCurrent)
                .containsExactly(startedEarly.appointmentId(), "IN_PROGRESS", true);
        assertThat(view.nextAppointment()).extracting(SpecialistTodayService.AppointmentView::appointmentId,
                SpecialistTodayService.AppointmentView::status)
                .containsExactly(scheduled.appointmentId(), "SCHEDULED");
    }

    @Test
    void includes_past_end_in_progress_appointment_in_current_operational_day_only() {
        Fixture fixture = fixture();
        var startedPastEnd = appointment(fixture.participantId, NOW.minusSeconds(2 * 3600), NOW.minusSeconds(3600), "IN_PROGRESS", true);
        when(fixture.appointments.inRange(eq(fixture.specialistId), any(), any(), eq(Set.of(fixture.participantId)), eq(NOW)))
                .thenReturn(List.of());
        when(fixture.appointments.inProgress(fixture.specialistId, Set.of(fixture.participantId), NOW)).thenReturn(List.of(startedPastEnd));

        var view = fixture.service.today("specialist", LocalDate.of(2030, 6, 10));

        assertThat(view.currentAppointment()).extracting(SpecialistTodayService.AppointmentView::appointmentId,
                SpecialistTodayService.AppointmentView::status).containsExactly(startedPastEnd.appointmentId(), "IN_PROGRESS");
        assertThat(view.appointments()).extracting(SpecialistTodayService.AppointmentView::appointmentId)
                .containsExactly(startedPastEnd.appointmentId());
        verify(fixture.appointments).inProgress(fixture.specialistId, Set.of(fixture.participantId), NOW);
    }

    @Test
    void does_not_add_in_progress_appointments_to_historical_day_view() {
        Fixture fixture = fixture();
        when(fixture.appointments.inRange(eq(fixture.specialistId), any(), any(), eq(Set.of(fixture.participantId)), eq(NOW)))
                .thenReturn(List.of());

        var view = fixture.service.today("specialist", LocalDate.of(2030, 6, 9));

        assertThat(view.currentAppointment()).isNull();
        verify(fixture.appointments, never()).inProgress(any(), any(), any());
    }

    private static SpecialistAppointmentQueryPort.OperationalAppointment appointment(UUID participantId, Instant startsAt,
                                                                                        Instant endsAt, String status, boolean current) {
        return new SpecialistAppointmentQueryPort.OperationalAppointment(UUID.randomUUID(), participantId, startsAt, endsAt,
                "TRAINING", status, "IN_PERSON", null, "Appointment", current, List.of(), 1L);
    }

    private static Fixture fixture() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistWorkspacePort workspace = mock(SpecialistWorkspacePort.class);
        ParticipantClientPort participants = mock(ParticipantClientPort.class);
        SpecialistAppointmentQueryPort appointments = mock(SpecialistAppointmentQueryPort.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(workspace.findProfile(specialistId)).thenReturn(Optional.of(new SpecialistWorkspacePort.Profile(specialistId,
                SpecialistWorkspacePort.WorkspaceRole.TRAINER, "UTC")));
        when(workspace.activeParticipantIds(specialistId)).thenReturn(Set.of(participantId));
        when(participants.find(participantId)).thenReturn(Optional.of(new ParticipantClientPort.ClientRecord(participantId,
                "Participant", ParticipantClientPort.RelationshipContext.CLIENT, ParticipantClientPort.RecordStatus.ACTIVE, 0)));
        RecurringAvailabilityService availability = mock(RecurringAvailabilityService.class);
        when(availability.list(specialistId)).thenReturn(List.of());
        AvailabilityCalendarPort calendarAvailability = mock(AvailabilityCalendarPort.class);
        when(calendarAvailability.bookableSlots(any(), any(), any())).thenReturn(List.of());
        SpecialistOverdueAppointmentQueryPort overdue = mock(SpecialistOverdueAppointmentQueryPort.class);
        when(overdue.overdueOutcomeAppointments(any(), any(), any())).thenReturn(List.of());
        SpecialistTodayService service = new SpecialistTodayService(accounts, workspace, participants, availability,
                calendarAvailability, appointments, overdue, mock(AuditRecorder.class), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(specialistId, participantId, appointments, service);
    }

    private record Fixture(UUID specialistId, UUID participantId, SpecialistAppointmentQueryPort appointments,
                           SpecialistTodayService service) { }
}
