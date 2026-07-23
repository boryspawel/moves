package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.*;
import org.junit.jupiter.api.Test;

class SpecialistTodayTimeTest {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Test void uses_local_day_boundaries_across_spring_dst_change() {
        LocalDate date = LocalDate.of(2026, 3, 29);
        assertThat(Duration.between(SpecialistTodayTime.startOfDay(date, WARSAW), SpecialistTodayTime.endOfDay(date, WARSAW))).isEqualTo(Duration.ofHours(23));
    }

    @Test void uses_local_day_boundaries_across_autumn_dst_change() {
        LocalDate date = LocalDate.of(2026, 10, 25);
        assertThat(Duration.between(SpecialistTodayTime.startOfDay(date, WARSAW), SpecialistTodayTime.endOfDay(date, WARSAW))).isEqualTo(Duration.ofHours(25));
    }

    @Test void includes_cross_midnight_appointment_in_each_intersecting_day_but_not_at_its_end_boundary() {
        Instant starts = Instant.parse("2026-07-23T22:00:00Z");
        Instant ends = Instant.parse("2026-07-24T02:00:00Z");
        assertThat(SpecialistTodayTime.intersects(starts, ends, LocalDate.of(2026, 7, 24), ZoneOffset.UTC)).isTrue();
        assertThat(SpecialistTodayTime.intersects(starts, ends, LocalDate.of(2026, 7, 25), ZoneOffset.UTC)).isFalse();
    }
}
