package com.motionecosystem.availability;

import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.availability.api.AvailabilityCalendarPort;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class RecurringAvailabilityService implements AvailabilityCalendarPort {

    public static final int DEFAULT_SLOT_DURATION_MINUTES = 50;
    private final RecurringSlotRepository slots;
    private final SlotDurationPreferenceRepository preferences;
    private final AdherenceMetricsService metrics;
    private final Clock clock;

    @Autowired
    public RecurringAvailabilityService(RecurringSlotRepository slots, SlotDurationPreferenceRepository preferences,
                                        AdherenceMetricsService metrics, Clock clock) {
        this.slots = slots;
        this.preferences = preferences;
        this.metrics = metrics;
        this.clock = clock;
    }

    RecurringAvailabilityService(RecurringSlotRepository slots, AdherenceMetricsService metrics, Clock clock) {
        this(slots, null, metrics, clock);
    }

    @Transactional
    public List<Slot> replace(UUID accountId, List<Slot> requested) {
        return replace(accountId, requested, null);
    }

    @Transactional
    public List<Slot> replace(UUID accountId, List<Slot> requested, Integer slotDurationMinutes) {
        List<Slot> validated = validate(requested);
        if (slotDurationMinutes != null) saveSlotDuration(accountId, slotDurationMinutes);
        slots.deleteByAccountId(accountId);
        slots.flush();
        slots.saveAll(validated.stream().map(slot -> new RecurringSlot(accountId, slot, clock.instant())).toList());
        metrics.ensureAssignments(accountId);
        metrics.record(accountId, "AVAILABILITY_REPLACED", accountId, null, null, null,
                "AVAILABILITY_V1", "CONFIGURED");
        return list(accountId);
    }

    @Transactional(readOnly = true)
    public boolean isConfigured(UUID accountId) {
        return slots.existsByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<Slot> list(UUID accountId) {
        return slots.findByAccountIdOrderByDayOfWeekAscStartTimeAsc(accountId).stream()
                .map(slot -> new Slot(slot.dayOfWeek, slot.startTime, slot.endTime, slot.timeZone))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Window> windows(UUID accountId, LocalDate date) {
        if (date == null) {
            throw invalid("availability date is required");
        }
        return list(accountId).stream()
                .filter(slot -> slot.dayOfWeek() == date.getDayOfWeek())
                .map(slot -> {
                    ZoneId zone = ZoneId.of(slot.timeZone());
                    return new Window(date.atTime(slot.startTime()).atZone(zone).toInstant(),
                            date.atTime(slot.endTime()).atZone(zone).toInstant());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public int slotDurationMinutes(UUID accountId) {
        if (preferences == null) return DEFAULT_SLOT_DURATION_MINUTES;
        return preferences.findById(accountId).map(item -> item.slotDurationMinutes)
                .orElse(DEFAULT_SLOT_DURATION_MINUTES);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookableSlot> bookableSlots(UUID accountId, LocalDate date, List<TimeRange> busyRanges) {
        java.time.Duration duration = java.time.Duration.ofMinutes(slotDurationMinutes(accountId));
        List<TimeRange> busy = busyRanges == null ? List.of() : List.copyOf(busyRanges);
        return normalized(windows(accountId, date)).stream().flatMap(window -> java.util.stream.Stream.iterate(window.startsAt(),
                        start -> start.plus(duration)).takeWhile(start -> !start.plus(duration).isAfter(window.endsAt()))
                .filter(start -> !overlaps(busy, start, start.plus(duration)))
                .map(start -> new BookableSlot(start, start.plus(duration)))).toList();
    }

    private static List<Window> normalized(List<Window> windows) {
        List<Window> ordered = windows.stream().sorted(java.util.Comparator.comparing(Window::startsAt)).toList();
        java.util.ArrayList<Window> result = new java.util.ArrayList<>();
        for (Window window : ordered) {
            if (result.isEmpty()) { result.add(window); continue; }
            Window previous = result.getLast();
            if (!window.startsAt().isAfter(previous.endsAt())) {
                result.set(result.size() - 1, new Window(previous.startsAt(),
                        window.endsAt().isAfter(previous.endsAt()) ? window.endsAt() : previous.endsAt()));
            } else result.add(window);
        }
        return List.copyOf(result);
    }

    private void saveSlotDuration(UUID accountId, int value) {
        if (value < 1 || value > 480) throw invalid("slot duration must be between 1 and 480 minutes");
        if (preferences == null) throw new IllegalStateException("slot duration preferences are not configured");
        SlotDurationPreference preference = preferences.findById(accountId)
                .orElseGet(() -> new SlotDurationPreference(accountId, value));
        preference.update(value);
        preferences.save(preference);
    }

    private static boolean overlaps(List<TimeRange> ranges, Instant start, Instant end) {
        return ranges.stream().anyMatch(range -> range.startsAt().isBefore(end) && range.endsAt().isAfter(start));
    }

    private static List<Slot> validate(List<Slot> requested) {
        if (requested == null || requested.isEmpty()) {
            throw invalid("at least one availability slot is required");
        }
        for (Slot slot : requested) {
            if (slot == null || slot.dayOfWeek == null || slot.startTime == null || slot.endTime == null
                    || slot.timeZone == null || !slot.endTime.isAfter(slot.startTime)) {
                throw invalid("availability slot has invalid boundaries");
            }
            try {
                ZoneId.of(slot.timeZone);
            } catch (ZoneRulesException invalidZone) {
                throw invalid("availability time zone is invalid");
            }
        }
        return List.copyOf(requested);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record Slot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String timeZone) {
    }

    public record Window(Instant startsAt, Instant endsAt) {
    }
}
