package com.motionecosystem.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RecurringAvailabilityServiceTest {
    @Test
    void defaults_and_persists_the_global_slot_duration() {
        RecurringSlotRepository slots = mock(RecurringSlotRepository.class);
        SlotDurationPreferenceRepository preferences = mock(SlotDurationPreferenceRepository.class);
        UUID accountId = UUID.randomUUID();
        when(preferences.findById(accountId)).thenReturn(java.util.Optional.empty());
        RecurringAvailabilityService service = new RecurringAvailabilityService(slots, preferences,
                mock(AdherenceMetricsService.class), Clock.system(ZoneOffset.UTC));

        assertThat(service.slotDurationMinutes(accountId)).isEqualTo(50);
        service.replace(accountId, List.of(new RecurringAvailabilityService.Slot(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), "UTC")), 45);

        org.mockito.Mockito.verify(preferences).save(org.mockito.ArgumentMatchers.argThat(item -> item.slotDurationMinutes == 45));
    }

    @Test
    void partitions_the_union_of_overlapping_windows_without_partial_or_busy_slots() {
        RecurringSlotRepository slots = mock(RecurringSlotRepository.class);
        SlotDurationPreferenceRepository preferences = mock(SlotDurationPreferenceRepository.class);
        UUID accountId = UUID.randomUUID();
        when(preferences.findById(accountId)).thenReturn(java.util.Optional.of(new SlotDurationPreference(accountId, 50)));
        when(slots.findByAccountIdOrderByDayOfWeekAscStartTimeAsc(accountId)).thenReturn(List.of(
                slot(DayOfWeek.MONDAY, 9, 0, 10, 0), slot(DayOfWeek.MONDAY, 9, 30, 11, 10),
                slot(DayOfWeek.MONDAY, 11, 10, 12, 0)));
        RecurringAvailabilityService service = new RecurringAvailabilityService(slots, preferences,
                mock(AdherenceMetricsService.class), Clock.system(ZoneOffset.UTC));

        var result = service.bookableSlots(accountId, LocalDate.of(2030, 6, 10), List.of(
                new com.motionecosystem.availability.api.AvailabilityCalendarPort.TimeRange(
                        Instant.parse("2030-06-10T09:50:00Z"), Instant.parse("2030-06-10T10:40:00Z"))));

        assertThat(result).containsExactly(
                new com.motionecosystem.availability.api.AvailabilityCalendarPort.BookableSlot(Instant.parse("2030-06-10T09:00:00Z"), Instant.parse("2030-06-10T09:50:00Z")),
                new com.motionecosystem.availability.api.AvailabilityCalendarPort.BookableSlot(Instant.parse("2030-06-10T10:40:00Z"), Instant.parse("2030-06-10T11:30:00Z")));
    }

    @Test
    void keeps_instant_duration_across_spring_dst_change() {
        RecurringSlotRepository slots = mock(RecurringSlotRepository.class);
        UUID accountId = UUID.randomUUID();
        when(slots.findByAccountIdOrderByDayOfWeekAscStartTimeAsc(accountId)).thenReturn(List.of(new RecurringSlot(accountId,
                new RecurringAvailabilityService.Slot(DayOfWeek.SUNDAY, LocalTime.of(1, 0), LocalTime.of(4, 0), "Europe/Warsaw"), Instant.EPOCH)));
        RecurringAvailabilityService service = new RecurringAvailabilityService(slots, mock(AdherenceMetricsService.class), Clock.systemUTC());

        assertThat(service.bookableSlots(accountId, LocalDate.of(2026, 3, 29), List.of())).containsExactly(
                new com.motionecosystem.availability.api.AvailabilityCalendarPort.BookableSlot(Instant.parse("2026-03-29T00:00:00Z"), Instant.parse("2026-03-29T00:50:00Z")),
                new com.motionecosystem.availability.api.AvailabilityCalendarPort.BookableSlot(Instant.parse("2026-03-29T00:50:00Z"), Instant.parse("2026-03-29T01:40:00Z")));
    }

    private static RecurringSlot slot(DayOfWeek day, int startHour, int startMinute, int endHour, int endMinute) {
        return new RecurringSlot(UUID.randomUUID(), new RecurringAvailabilityService.Slot(day,
                LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute), "UTC"), Instant.EPOCH);
    }
    @Test
    void accepts_overlapping_weekly_windows_in_the_same_time_zone() {
        RecurringSlotRepository repository = mock(RecurringSlotRepository.class);
        UUID accountId = UUID.randomUUID();
        when(repository.findByAccountIdOrderByDayOfWeekAscStartTimeAsc(accountId)).thenReturn(List.of());
        RecurringAvailabilityService service = new RecurringAvailabilityService(repository,
                mock(AdherenceMetricsService.class), Clock.system(ZoneOffset.UTC));

        List<RecurringAvailabilityService.Slot> result = service.replace(accountId, List.of(
                new RecurringAvailabilityService.Slot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), "Europe/Warsaw"),
                new RecurringAvailabilityService.Slot(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0), "Europe/Warsaw")));

        assertThat(result).isEmpty();
    }

    @Test
    void accepts_adjacent_windows_in_the_same_time_zone() {
        RecurringSlotRepository repository = mock(RecurringSlotRepository.class);
        UUID accountId = UUID.randomUUID();
        when(repository.findByAccountIdOrderByDayOfWeekAscStartTimeAsc(accountId)).thenReturn(List.of());
        RecurringAvailabilityService service = new RecurringAvailabilityService(repository,
                mock(AdherenceMetricsService.class), Clock.system(ZoneOffset.UTC));

        List<RecurringAvailabilityService.Slot> result = service.replace(accountId, List.of(
                new RecurringAvailabilityService.Slot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), "Europe/Warsaw"),
                new RecurringAvailabilityService.Slot(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0), "Europe/Warsaw")));

        assertThat(result).isEmpty();
    }
}
