package com.motionecosystem.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialistAvailableSlotsServiceTest {
    private final UUID specialistId = UUID.randomUUID();
    private final CurrentAccountService accounts = mock(CurrentAccountService.class);
    private final SpecialistWorkspacePort specialistWorkspace = mock(SpecialistWorkspacePort.class);
    private final RecurringAvailabilityService availability = mock(RecurringAvailabilityService.class);
    private final SpecialistAppointmentQueryPort appointments = mock(SpecialistAppointmentQueryPort.class);

    @Test
    void returns_only_full_grid_slots_that_do_not_collide() {
        LocalDate date = LocalDate.of(2030, 6, 10);
        SpecialistAvailableSlotsService service = service(Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC), "UTC");
        when(availability.windows(specialistId, date)).thenReturn(List.of(new RecurringAvailabilityService.Window(
                Instant.parse("2030-06-10T08:00:00Z"), Instant.parse("2030-06-10T10:00:00Z"))));
        when(appointments.blockingInRange(specialistId, Instant.parse("2030-06-10T00:00:00Z"), Instant.parse("2030-06-11T00:00:00Z")))
                .thenReturn(List.of(new SpecialistAppointmentQueryPort.TimeRange(Instant.parse("2030-06-10T09:00:00Z"), Instant.parse("2030-06-10T09:30:00Z"))));

        var result = service.list("specialist", date, 60);

        assertThat(result.slots()).containsExactly(new SpecialistAvailableSlotsService.SlotView(
                Instant.parse("2030-06-10T08:00:00Z"), Instant.parse("2030-06-10T09:00:00Z")));
    }

    @Test
    void keeps_instant_duration_and_grid_across_spring_dst_change() {
        LocalDate date = LocalDate.of(2026, 3, 29);
        SpecialistAvailableSlotsService service = service(Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC), "Europe/Warsaw");
        when(availability.windows(specialistId, date)).thenReturn(List.of(new RecurringAvailabilityService.Window(
                Instant.parse("2026-03-29T00:00:00Z"), Instant.parse("2026-03-29T02:00:00Z"))));
        when(appointments.blockingInRange(specialistId, Instant.parse("2026-03-28T23:00:00Z"), Instant.parse("2026-03-29T22:00:00Z"))).thenReturn(List.of());

        var result = service.list("specialist", date, 60);

        assertThat(result.slots()).hasSize(3).allSatisfy(slot -> assertThat(java.time.Duration.between(slot.startAt(), slot.endAt()))
                .isEqualTo(java.time.Duration.ofHours(1)));
        assertThat(result.slots().get(1).startAt()).isEqualTo(Instant.parse("2026-03-29T00:30:00Z"));
    }

    private SpecialistAvailableSlotsService service(Clock clock, String timeZone) {
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(specialistWorkspace.findProfile(specialistId)).thenReturn(Optional.of(new SpecialistWorkspacePort.Profile(
                specialistId, SpecialistWorkspacePort.WorkspaceRole.TRAINER, timeZone)));
        return new SpecialistAvailableSlotsService(accounts, specialistWorkspace, availability, appointments, clock);
    }
}
