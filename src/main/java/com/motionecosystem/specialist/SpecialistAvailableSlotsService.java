package com.motionecosystem.specialist;

import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.calendar.AppointmentService;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
class SpecialistAvailableSlotsService {
    private static final Duration GRID = Duration.ofMinutes(30);
    private final CurrentAccountService accounts;
    private final SpecialistProfileService profiles;
    private final RecurringAvailabilityService availability;
    private final AppointmentService appointments;
    private final Clock clock;

    @Transactional(readOnly = true)
    AvailableSlotsView list(String subject, LocalDate date, int durationMinutes) {
        if (date == null || durationMinutes < 1 || durationMinutes > 480) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date and durationMinutes (1-480) are required");
        }
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required");
        }
        ZoneId zone = profiles.find(account.id()).map(SpecialistProfileService.ProfileView::timeZoneId)
                .map(SpecialistAvailableSlotsService::zone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "specialist profile is required"));
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<AppointmentService.TimeRange> blocking = appointments.blockingInRange(account.id(), dayStart, dayEnd);
        List<SlotView> slots = new ArrayList<>();
        for (RecurringAvailabilityService.Window window : availability.windows(account.id(), date)) {
            for (Instant start : gridStarts(date, zone, window)) {
                Instant end = start.plus(Duration.ofMinutes(durationMinutes));
                if (end.isAfter(window.endsAt()) || !isFutureForToday(start, date, zone) || overlaps(blocking, start, end)) {
                    continue;
                }
                slots.add(new SlotView(start, end));
            }
        }
        slots.sort(Comparator.comparing(SlotView::startAt));
        return new AvailableSlotsView(date, zone.getId(), durationMinutes, List.copyOf(slots));
    }

    private List<Instant> gridStarts(LocalDate date, ZoneId profileZone, RecurringAvailabilityService.Window window) {
        Instant dayStart = date.atStartOfDay(profileZone).toInstant();
        Instant first = dayStart;
        while (first.isBefore(window.startsAt())) first = first.plus(GRID);
        return java.util.stream.Stream.iterate(first, value -> value.plus(GRID))
                .takeWhile(value -> value.isBefore(window.endsAt()))
                .toList();
    }

    private boolean isFutureForToday(Instant start, LocalDate date, ZoneId zone) {
        return !date.equals(LocalDate.now(clock.withZone(zone))) || start.isAfter(clock.instant());
    }
    private static boolean overlaps(List<AppointmentService.TimeRange> ranges, Instant start, Instant end) {
        return ranges.stream().anyMatch(range -> range.startsAt().isBefore(end) && range.endsAt().isAfter(start));
    }
    private static ZoneId zone(String value) { try { return ZoneId.of(value); } catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.CONFLICT, "specialist time zone is invalid"); } }

    record AvailableSlotsView(LocalDate date, String timezone, int durationMinutes, List<SlotView> slots) { }
    record SlotView(Instant startAt, Instant endAt) { }
}
