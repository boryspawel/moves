package com.motionecosystem.calendar;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable audit history; Appointment remains the current-state aggregate, not an event-sourced aggregate. */
@Entity
@Table(name = "appointment_event", schema = "calendar")
class AppointmentEvent {
    enum Type { CREATED, UPDATED, RESCHEDULED, COMPLETED, CANCELLED, NO_SHOW, BASELINE }

    @Id UUID id;
    @Column(name = "appointment_id", nullable = false) UUID appointmentId;
    @Column(name = "specialist_account_id", nullable = false) UUID specialistAccountId;
    @Column(name = "participant_id", nullable = false) UUID participantId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false) Type eventType;
    @Enumerated(EnumType.STRING) @Column(name = "from_status") Appointment.Status fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false) Appointment.Status toStatus;
    @Column(name = "effective_at", nullable = false) Instant effectiveAt;
    @Column(name = "recorded_at", nullable = false) Instant recordedAt;
    @Column(name = "actor_account_id") UUID actorAccountId;
    @Column(name = "starts_at", nullable = false) Instant startsAt;
    @Column(name = "ends_at", nullable = false) Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(name = "type", nullable = false) Appointment.Type type;
    @Enumerated(EnumType.STRING) @Column(name = "location_mode", nullable = false) Appointment.LocationMode locationMode;
    @Column String location;
    @Column(name = "short_purpose") String shortPurpose;
    @Column(name = "previous_starts_at") Instant previousStartsAt;
    @Column(name = "previous_ends_at") Instant previousEndsAt;

    protected AppointmentEvent() { }
    AppointmentEvent(Appointment appointment, Type type, Appointment.Status fromStatus, Instant effectiveAt,
                     Instant recordedAt, UUID actorAccountId, Instant previousStartsAt, Instant previousEndsAt) {
        id = UUID.randomUUID(); appointmentId = appointment.id; specialistAccountId = appointment.specialistAccountId;
        participantId = appointment.participantId; eventType = type; this.fromStatus = fromStatus; toStatus = appointment.status;
        this.effectiveAt = effectiveAt; this.recordedAt = recordedAt; this.actorAccountId = actorAccountId;
        startsAt = appointment.startsAt; endsAt = appointment.endsAt; this.type = appointment.type;
        locationMode = appointment.locationMode; location = appointment.location; shortPurpose = appointment.shortPurpose;
        this.previousStartsAt = previousStartsAt; this.previousEndsAt = previousEndsAt;
    }
}
