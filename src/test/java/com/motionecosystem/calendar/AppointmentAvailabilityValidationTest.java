package com.motionecosystem.calendar;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistKind;
import com.motionecosystem.specialist.SpecialistProfileService;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AppointmentAvailabilityValidationTest {
    @Test
    void rejects_create_and_update_outside_configured_availability_before_collision_checks() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        AppointmentRepository repository = mock(AppointmentRepository.class);
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        RecurringAvailabilityService availability = mock(RecurringAvailabilityService.class);
        SpecialistProfileService profiles = mock(SpecialistProfileService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(profiles.find(specialistId)).thenReturn(Optional.of(new SpecialistProfileService.ProfileView(
                specialistId, "Specialist", SpecialistKind.TRAINER, "UTC")));
        Instant starts = Instant.parse("2030-06-10T12:00:00Z");
        Instant ends = Instant.parse("2030-06-10T13:00:00Z");
        when(availability.windows(specialistId, starts.atZone(ZoneOffset.UTC).toLocalDate())).thenReturn(List.of(
                new RecurringAvailabilityService.Window(Instant.parse("2030-06-10T08:00:00Z"), Instant.parse("2030-06-10T10:00:00Z"))));
        AppointmentService service = new AppointmentService(repository, mock(AppointmentEventRepository.class), mock(AppointmentIdempotencyRepository.class), accounts,
                mock(SpecialistRelationshipService.class), availability, profiles, mock(AuditRecorder.class),
                Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC));
        AppointmentService.CreateCommand create = new AppointmentService.CreateCommand(participantId, starts, ends,
                Appointment.Type.CONSULTATION, Appointment.LocationMode.REMOTE, null, null);
        Appointment existing = new Appointment(specialistId, participantId, Instant.parse("2030-06-10T08:00:00Z"),
                Instant.parse("2030-06-10T09:00:00Z"), Appointment.Type.CONSULTATION, Appointment.LocationMode.REMOTE,
                null, null, specialistId, Instant.now());
        when(repository.findById(existing.id)).thenReturn(Optional.of(existing));
        AppointmentService.UpdateCommand update = new AppointmentService.UpdateCommand(participantId, starts, ends,
                Appointment.Type.CONSULTATION, Appointment.LocationMode.REMOTE, null, null, existing.version);

        assertThatThrownBy(() -> service.create("specialist", "create-key", create))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("within specialist availability");
        assertThatThrownBy(() -> service.update("specialist", existing.id, "update-key", update))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("within specialist availability");

        verify(repository, never()).hasActiveOverlap(specialistId, starts, ends, existing.id);
    }
}
