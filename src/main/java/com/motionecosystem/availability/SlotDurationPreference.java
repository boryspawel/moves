package com.motionecosystem.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "slot_duration_preference", schema = "availability")
class SlotDurationPreference {
    @Id UUID accountId;
    @Column(name = "slot_duration_minutes", nullable = false) int slotDurationMinutes;
    protected SlotDurationPreference() { }
    SlotDurationPreference(UUID accountId, int slotDurationMinutes) { this.accountId = accountId; this.slotDurationMinutes = slotDurationMinutes; }
    void update(int value) { slotDurationMinutes = value; }
}
