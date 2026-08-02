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
import org.junit.jupiter.api.Test;

class RecurringAvailabilityServiceTest {
    @Test
    void accepts_overlapping_weekly_windows_when_boundaries_are_valid() {
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
}
