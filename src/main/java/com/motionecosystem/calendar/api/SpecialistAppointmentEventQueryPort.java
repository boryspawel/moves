package com.motionecosystem.calendar.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only appointment history projection for an already authorized specialist-participant relationship. */
public interface SpecialistAppointmentEventQueryPort {
    List<AppointmentEventSummary> timeline(UUID specialistAccountId, UUID participantId,
                                           Instant fromInclusive, Instant toExclusive, SeekCursor after, int limit);

    Optional<AppointmentEventSummary> findBySpecialistAndParticipant(UUID specialistAccountId, UUID participantId, UUID eventId);

    record SeekCursor(Instant effectiveAt, Instant recordedAt, UUID eventId) { }
    record AppointmentEventSummary(UUID eventId, UUID appointmentId, String eventType, String fromStatus, String toStatus,
                                   Instant effectiveAt, Instant recordedAt, String appointmentType, String shortPurpose) { }
}
