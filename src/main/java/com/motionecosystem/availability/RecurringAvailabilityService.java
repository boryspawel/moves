package com.motionecosystem.availability;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecurringAvailabilityService {

    private final RecurringSlotRepository slots;
    private final Clock clock;

    RecurringAvailabilityService(RecurringSlotRepository slots, Clock clock) {
        this.slots = slots;
        this.clock = clock;
    }

    @Transactional
    public List<Slot> replace(UUID accountId, List<Slot> requested) {
        List<Slot> validated = validate(requested);
        slots.deleteByAccountId(accountId);
        slots.flush();
        slots.saveAll(validated.stream().map(slot -> new RecurringSlot(accountId, slot, clock.instant())).toList());
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

    private static List<Slot> validate(List<Slot> requested) {
        if (requested == null || requested.isEmpty()) {
            throw invalid("at least one availability slot is required");
        }
        List<Slot> sorted = new ArrayList<>(requested);
        for (Slot slot : sorted) {
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
        sorted.sort(Comparator.comparing(Slot::dayOfWeek)
                .thenComparing(Slot::timeZone)
                .thenComparing(Slot::startTime));
        for (int index = 1; index < sorted.size(); index++) {
            Slot previous = sorted.get(index - 1);
            Slot current = sorted.get(index);
            if (previous.dayOfWeek == current.dayOfWeek
                    && previous.timeZone.equals(current.timeZone)
                    && current.startTime.isBefore(previous.endTime)) {
                throw invalid("availability slots overlap");
            }
        }
        return List.copyOf(sorted);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record Slot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String timeZone) {
    }
}
