package com.motionecosystem.availability.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilityCalendarPort {
    List<BookableSlot> bookableSlots(UUID accountId, LocalDate date, List<TimeRange> busyRanges);
    record TimeRange(Instant startsAt, Instant endsAt) { }
    record BookableSlot(Instant startsAt, Instant endsAt) { }
}
