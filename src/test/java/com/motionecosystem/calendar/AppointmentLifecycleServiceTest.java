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
import com.motionecosystem.calendar.api.CalendarSpecialistContextPort;
import com.motionecosystem.calendar.api.AppointmentExecutionStatusPort;
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

        verify(fixture.relationships, never()).requireActiveRelationship(any(), any());
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
    void requires_recorded_linked_execution_before_completion_but_allows_unlinked_and_idempotent_replay() {
        Fixture fixture = fixture();
        Appointment unlinked = appointment(fixture.specialistId, fixture.participantId, NOW.minusSeconds(120), NOW.plusSeconds(60));
        when(fixture.appointments.findById(unlinked.id)).thenReturn(Optional.of(unlinked));
        when(fixture.appointments.saveAndFlush(unlinked)).thenReturn(unlinked);
        assertThat(fixture.service.complete("specialist", unlinked.id, "unlinked-complete",
                new AppointmentService.AppointmentVersionCommand(unlinked.version)).status()).isEqualTo(Appointment.Status.COMPLETED);

        Appointment linked = appointment(fixture.specialistId, fixture.participantId, NOW.minusSeconds(120), NOW.plusSeconds(60));
        linked.plannedSessionId = UUID.randomUUID();
        when(fixture.appointments.findById(linked.id)).thenReturn(Optional.of(linked));
        when(fixture.executionStatus.hasRecordedExecution(linked.plannedSessionId)).thenReturn(false);
        assertThatThrownBy(() -> fixture.service.complete("specialist", linked.id, "linked-complete",
                new AppointmentService.AppointmentVersionCommand(linked.version)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("execution must be recorded");

        when(fixture.executionStatus.hasRecordedExecution(linked.plannedSessionId)).thenReturn(true);
        when(fixture.appointments.saveAndFlush(linked)).thenReturn(linked);
        assertThat(fixture.service.complete("specialist", linked.id, "linked-complete",
                new AppointmentService.AppointmentVersionCommand(linked.version)).status()).isEqualTo(Appointment.Status.COMPLETED);
        when(fixture.idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(fixture.specialistId,
                "COMPLETE:" + linked.id, "linked-complete"))
                .thenReturn(Optional.of(new AppointmentIdempotency(fixture.specialistId, "COMPLETE:" + linked.id,
                        "linked-complete", linked.id, NOW)));
        when(fixture.executionStatus.hasRecordedExecution(linked.plannedSessionId)).thenReturn(false);
        assertThat(fixture.service.complete("specialist", linked.id, "linked-complete",
                new AppointmentService.AppointmentVersionCommand(linked.version)).status()).isEqualTo(Appointment.Status.COMPLETED);
    }

    @Test
    void starts_owned_appointment_with_event_audit_available_action_and_idempotent_replay() {
        Fixture fixture = fixture();
        Appointment appointment = appointment(fixture.specialistId, fixture.participantId, NOW.plusSeconds(20 * 60), NOW.plusSeconds(80 * 60));
        when(fixture.appointments.findById(appointment.id)).thenReturn(Optional.of(appointment));
        when(fixture.appointments.saveAndFlush(appointment)).thenReturn(appointment);
        AppointmentService.AppointmentVersionCommand command = new AppointmentService.AppointmentVersionCommand(appointment.version);

        AppointmentService.AppointmentView started = fixture.service.start("specialist", appointment.id, "start-key", command);

        assertThat(started.status()).isEqualTo(Appointment.Status.IN_PROGRESS);
        assertThat(started.availableActions()).doesNotContain("START").contains("COMPLETE");
        verify(fixture.relationships).requireActiveRelationship(fixture.specialistId, fixture.participantId);
        verify(fixture.audit).record("specialist", "APPOINTMENT_STARTED", "Appointment", appointment.id);
        ArgumentCaptor<AppointmentEvent> event = ArgumentCaptor.forClass(AppointmentEvent.class);
        verify(fixture.events).save(event.capture());
        assertThat(event.getValue().eventType).isEqualTo(AppointmentEvent.Type.STARTED);
        assertThat(event.getValue().fromStatus).isEqualTo(Appointment.Status.SCHEDULED);
        assertThat(event.getValue().toStatus).isEqualTo(Appointment.Status.IN_PROGRESS);

        when(fixture.idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(fixture.specialistId, "START:" + appointment.id, "start-key"))
                .thenReturn(Optional.of(new AppointmentIdempotency(fixture.specialistId, "START:" + appointment.id, "start-key", appointment.id, NOW)));
        assertThat(fixture.service.start("specialist", appointment.id, "start-key", command).status()).isEqualTo(Appointment.Status.IN_PROGRESS);
        verify(fixture.events).save(event.capture());
        verify(fixture.audit).record("specialist", "APPOINTMENT_STARTED", "Appointment", appointment.id);
    }

    @Test
    void rejects_start_before_window_after_end_and_from_non_schedulable_states() {
        Fixture fixture = fixture();
        AppointmentService.AppointmentVersionCommand command = new AppointmentService.AppointmentVersionCommand(0L);
        assertStartRejected(fixture, appointment(fixture.specialistId, fixture.participantId, NOW.plusSeconds(30 * 60 + 1), NOW.plusSeconds(90 * 60)), command);
        assertStartRejected(fixture, appointment(fixture.specialistId, fixture.participantId, NOW.minusSeconds(90 * 60), NOW.minusSeconds(1)), command);
        for (Appointment.Status status : List.of(Appointment.Status.IN_PROGRESS, Appointment.Status.COMPLETED,
                Appointment.Status.CANCELLED, Appointment.Status.NO_SHOW)) {
            Appointment appointment = appointment(fixture.specialistId, fixture.participantId, NOW.minusSeconds(5 * 60), NOW.plusSeconds(5 * 60));
            appointment.status = status;
            assertStartRejected(fixture, appointment, command);
        }
    }

    @Test
    void rejects_start_with_stale_version_before_mutation() {
        Fixture fixture = fixture();
        Appointment appointment = appointment(fixture.specialistId, fixture.participantId, NOW.plusSeconds(5 * 60), NOW.plusSeconds(65 * 60));
        appointment.version = 1L;
        when(fixture.appointments.findById(appointment.id)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> fixture.service.start("specialist", appointment.id, "start-key", new AppointmentService.AppointmentVersionCommand(0L)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("version is stale");
        verify(fixture.appointments, never()).saveAndFlush(any());
        verify(fixture.events, never()).save(any());
        verify(fixture.audit, never()).record(any(), any(), any(), any());
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
        verify(fixture.relationships).requireActiveRelationship(fixture.specialistId, fixture.participantId);
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

    @Test
    void in_progress_projection_remains_current_before_start_and_after_end() {
        Fixture fixture = fixture();
        Appointment earlyStarted = appointment(fixture.specialistId, fixture.participantId, NOW.plusSeconds(20 * 60), NOW.plusSeconds(80 * 60));
        earlyStarted.status = Appointment.Status.IN_PROGRESS;
        Appointment pastEndStarted = appointment(fixture.specialistId, UUID.randomUUID(), NOW.minusSeconds(80 * 60), NOW.minusSeconds(20 * 60));
        pastEndStarted.status = Appointment.Status.IN_PROGRESS;
        Set<UUID> participants = Set.of(earlyStarted.participantId, pastEndStarted.participantId);
        when(fixture.appointments.findInProgress(fixture.specialistId, participants)).thenReturn(List.of(earlyStarted, pastEndStarted));

        var result = fixture.service.inProgress(fixture.specialistId, participants, NOW);

        assertThat(result).extracting(com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort.OperationalAppointment::appointmentId,
                com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort.OperationalAppointment::current)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(earlyStarted.id, true),
                        org.assertj.core.groups.Tuple.tuple(pastEndStarted.id, true));
    }

    private static Fixture fixture() {
        UUID specialistId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        AppointmentRepository appointments = mock(AppointmentRepository.class);
        AppointmentEventRepository events = mock(AppointmentEventRepository.class);
        AppointmentIdempotencyRepository idempotency = mock(AppointmentIdempotencyRepository.class);
        CalendarSpecialistContextPort specialistContext = mock(CalendarSpecialistContextPort.class);
        AppointmentExecutionStatusPort executionStatus = mock(AppointmentExecutionStatusPort.class);
        RecurringAvailabilityService availability = mock(RecurringAvailabilityService.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        return new Fixture(specialistId, UUID.randomUUID(), appointments, events, idempotency, specialistContext, availability, executionStatus, audit,
                new AppointmentService(appointments, events, idempotency, accounts, specialistContext, availability,
                        (subject, participantId, plannedSessionId) -> { }, executionStatus, audit, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static Appointment appointment(UUID specialist, UUID participant, Instant startsAt, Instant endsAt) {
        return new Appointment(specialist, participant, startsAt, endsAt, Appointment.Type.CONSULTATION,
                Appointment.LocationMode.REMOTE, null, null, specialist, NOW.minusSeconds(600));
    }

    private static void assertStartRejected(Fixture fixture, Appointment appointment, AppointmentService.AppointmentVersionCommand command) {
        when(fixture.appointments.findById(appointment.id)).thenReturn(Optional.of(appointment));
        assertThatThrownBy(() -> fixture.service.start("specialist", appointment.id, "start-" + appointment.id, command))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cannot start");
    }

    private record Fixture(UUID specialistId, UUID participantId, AppointmentRepository appointments, AppointmentEventRepository events,
                           AppointmentIdempotencyRepository idempotency, CalendarSpecialistContextPort relationships,
                           RecurringAvailabilityService availability, AppointmentExecutionStatusPort executionStatus,
                           AuditRecorder audit, AppointmentService service) { }
}
