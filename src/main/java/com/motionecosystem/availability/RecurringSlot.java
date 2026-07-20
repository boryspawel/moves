package com.motionecosystem.availability;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recurring_slot", schema = "availability")
class RecurringSlot {

    @Id
    UUID id;
    @Column(name = "account_id", nullable = false)
    UUID accountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    DayOfWeek dayOfWeek;
    @Column(name = "start_time", nullable = false)
    LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    LocalTime endTime;
    @Column(name = "time_zone", nullable = false)
    String timeZone;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected RecurringSlot() {
    }

    RecurringSlot(UUID accountId, RecurringAvailabilityService.Slot slot, Instant now) {
        id = UUID.randomUUID();
        this.accountId = accountId;
        dayOfWeek = slot.dayOfWeek();
        startTime = slot.startTime();
        endTime = slot.endTime();
        timeZone = slot.timeZone();
        createdAt = now;
    }
}
