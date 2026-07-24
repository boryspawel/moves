package com.motionecosystem.calendar.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded appointment projection for an already authorized specialist-participant relationship. */
public interface SpecialistAppointmentQueryPort {

    List<AppointmentSummary> findForParticipant(UUID specialistAccountId, UUID participantAccountId,
                                                Instant fromInclusive, Instant toExclusive, int limit);

    /** Bounded seek page ordered as the specialist participant timeline. */
    List<AppointmentSummary> timeline(UUID specialistAccountId, UUID participantAccountId,
                                      Instant fromInclusive, Instant toExclusive, SeekCursor after, int limit);

    record SeekCursor(Instant effectiveFrom, Instant recordedAt, String eventId) { }

    record AppointmentSummary(UUID appointmentId, Instant startsAt, Instant endsAt, String type,
                              String status, String shortPurpose, Instant recordedAt, Instant updatedAt) { }
}
