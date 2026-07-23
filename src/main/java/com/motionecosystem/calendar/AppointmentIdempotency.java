package com.motionecosystem.calendar;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_idempotency", schema = "calendar", uniqueConstraints = @UniqueConstraint(name = "uq_calendar_appointment_idempotency", columnNames = {"specialist_account_id", "operation", "idempotency_key"}))
class AppointmentIdempotency {
    @Id UUID id;
    @Column(name = "specialist_account_id", nullable = false) UUID specialistAccountId;
    @Column(nullable = false) String operation;
    @Column(name = "idempotency_key", nullable = false) String idempotencyKey;
    @Column(name = "appointment_id", nullable = false) UUID appointmentId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected AppointmentIdempotency() { }
    AppointmentIdempotency(UUID specialist, String operation, String key, UUID appointment, Instant now) {
        id = UUID.randomUUID(); specialistAccountId = specialist; this.operation = operation; idempotencyKey = key; appointmentId = appointment; createdAt = now;
    }
}
