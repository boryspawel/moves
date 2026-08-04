package com.motionecosystem.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistProfileService;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class AppointmentLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Test
    void rejects_historical_update_before_relationship_availability_or_overlap_checks() {
        Fixture fixture = fixture();
        Appointment appointment = appointment(fixture.specialistId, fixture.participantId, NOW.minusSeconds(120), NOW.minusSeconds(60));
        when(fixture.appointments.findById(appointment.id)).thenReturn(Optional.of(appointment));
        AppointmentService.UpdateCommand command = new AppointmentService.UpdateCommand(fixture.participantId,
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), Appointment.Type.CONSULTATION,
                Appointment.LocationMode.REMOTE, null, null, appointment.version);

        assertThatThrownBy(() -> fixture.service.update("specialist", appointment.id, "update-key", command))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cannot update");

        verify(fixture.relationships, never()).requireActive(any(), any());
        verify(fixture.availability, never()).windows(any(), any());
        verify(fixture.appointments, never()).hasActiveOverlap(any(), any(), any(), any());
    }

    @Test
    void completes_owned_eligible_appointment_with_audit_and_replays_same_idempotency_key() {
        Fixture fixture = fixture();
        Appointment appointment = appointment(fixture.specialistId, fixture.participantId, NOW.minusSeconds(120), NOW.plusSeconds(60));
        when(fixture.appointments.findById(appointment.id)).thenReturn(Optional.of(appointment));
        when(fixture.appointments.saveAndFlush(appointment)).thenReturn(appointment);
        AppointmentService.AppointmentVersionCommand command = new AppointmentService.AppointmentVersionCommand(appointment.version);

        AppointmentService.AppointmentView completed = fixture.service.complete("specialist", appointment.id, "complete-key", command);

        assertThat(completed.status()).isEqualTo(Appointment.Status.COMPLETED);
        verify(fixture.audit).record("specialist", "APPOINTMENT_COMPLETED", "Appointment", appointment.id);
        ArgumentCaptor<AppointmentEvent> event = ArgumentCaptor.forClass(AppointmentEvent.class);
        verify(fixture.events).save(event.capture());
        assertThat(event.getValue().eventType).isEqualTo(AppointmentEvent.Type.COMPLETED);
        assertThat(event.getValue().fromStatus).isEqualTo(Appointment.Status.SCHEDULED);
        assertThat(event.getValue().toStatus).isEqualTo(Appointment.Status.COMPLETED);
        when(fixture.idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(fixture.specialistId, "COMPLETE:" + appointment.id, "complete-key"))
                .thenReturn(Optional.of(new AppointmentIdempotency(fixture.specialistId, "COMPLETE:" + appointment.id, "complete-key", appointment.id, NOW)));

        assertThat(fixture.service.complete("specialist", appointment.id, "complete-key", command).status()).isEqualTo(Appointment.Status.COMPLETED);
        verify(fixture.audit).record("specialist", "APPOINTMENT_COMPLETED", "Appointment", appointment.id);
    }

    @Test
    void detail_requires_owned_active_relationship_and_returns_current_view() {
        Fixture fixture = fixture();
        Appointment appointment = appointment(fixture.specialistId, fixture.participantId, NOW.minusSeconds(120), NOW.plusSeconds(60));
        when(fixture.appointments.findById(appointment.id)).thenReturn(Optional.of(appointment));

        AppointmentService.AppointmentView result = fixture.service.detail("specialist", appointment.id);

        assertThat(result.appointmentId()).isEqualTo(appointment.id);
        assertThat(result.version()).isEqualTo(appointment.version);
        assertThat(result.availableActions()).contains("COMPLETE");
        verify(fixture.relationships).requireActive(fixture.specialistId, fixture.participantId);
    }

    @Test
    void overdue_outcome_projection_uses_policy_order_and_latest_event_fallback() {
        Fixture fixture = fixture();
        UUID firstParticipant = UUID.randomUUID();
        UUID secondParticipant = UUID.randomUUID();
        Appointment first = appointment(fixture.specialistId, firstParticipant, NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        Appointment second = appointment(fixture.specialistId, secondParticipant, NOW.minusSeconds(3600), NOW.minusSeconds(60));
        UUID eventId = UUID.randomUUID();
        when(fixture.appointments.findOverdueOutcomeAppointments(org.mockito.ArgumentMatchers.eq(fixture.specialistId),
                org.mockito.ArgumentMatchers.eq(Set.of(firstParticipant, secondParticipant)), org.mockito.ArgumentMatchers.eq(NOW),
                org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(first, second));
        when(fixture.events.findLatestEventId(org.mockito.ArgumentMatchers.eq(first.id), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(eventId));
        when(fixture.events.findLatestEventId(org.mockito.ArgumentMatchers.eq(second.id), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var results = fixture.service.overdueOutcomeAppointments(fixture.specialistId, Set.of(firstParticipant, secondParticipant), NOW);

        assertThat(results).extracting(com.motionecosystem.calendar.api.SpecialistOverdueAppointmentQueryPort.OverdueAppointment::appointmentId)
                .containsExactly(first.id, second.id);
        assertThat(results).extracting(com.motionecosystem.calendar.api.SpecialistOverdueAppointmentQueryPort.OverdueAppointment::latestEventId)
                .containsExactly(eventId, null);
    }

    private static Fixture fixture() {
        UUID specialistId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        AppointmentEventRepository events = mock(AppointmentEventRepository.class);
        AppointmentIdempotencyRepository idempotency = mock(AppointmentIdempotencyRepository.class);
        SpecialistRelationshipService relationships = mock(SpecialistRelationshipService.class);
        RecurringAvailabilityService availability = mock(RecurringAvailabilityService.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        return new Fixture(specialistId, UUID.randomUUID(), appointments, events, idempotency, relationships, availability, audit,
                new AppointmentService(appointments, events, idempotency, accounts, relationships, availability,
                        mock(SpecialistProfileService.class), audit, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static Appointment appointment(UUID specialist, UUID participant, Instant startsAt, Instant endsAt) {
        return new Appointment(specialist, participant, startsAt, endsAt, Appointment.Type.CONSULTATION,
                Appointment.LocationMode.REMOTE, null, null, specialist, NOW.minusSeconds(600));
    }

    private record Fixture(UUID specialistId, UUID participantId, AppointmentRepository appointments, AppointmentEventRepository events,
                           AppointmentIdempotencyRepository idempotency, SpecialistRelationshipService relationships,
                           RecurringAvailabilityService availability, AuditRecorder audit, AppointmentService service) { }
}
