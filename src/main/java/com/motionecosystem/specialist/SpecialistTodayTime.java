package com.motionecosystem.specialist;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class SpecialistTodayTime {
    private SpecialistTodayTime() { }
    static Instant startOfDay(LocalDate date, ZoneId zone) { return date.atStartOfDay(zone).toInstant(); }
    static Instant endOfDay(LocalDate date, ZoneId zone) { return date.plusDays(1).atStartOfDay(zone).toInstant(); }
    static boolean intersects(Instant startsAt, Instant endsAt, LocalDate date, ZoneId zone) {
        return startsAt.isBefore(endOfDay(date, zone)) && endsAt.isAfter(startOfDay(date, zone));
    }
}
