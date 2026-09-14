package com.motionecosystem.calendar.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded appointment projection for an already authorized specialist-participant relationship. */
public interface SpecialistAppointmentQueryPort {

    List<AppointmentSummary> findForParticipant(UUID specialistAccountId, UUID participantId,
                                                Instant fromInclusive, Instant toExclusive, int limit);

    List<TimeRange> blockingInRange(UUID specialistId, Instant fromInclusive, Instant toExclusive);

    List<ScheduledAppointment> inRange(UUID specialistId, Instant fromInclusive, Instant toExclusive,
                                       java.util.Set<UUID> activeParticipantIds, Instant now);

    record AppointmentSummary(UUID appointmentId, Instant startsAt, Instant endsAt, String type,
                              String status, String shortPurpose, Instant recordedAt, Instant updatedAt) { }
    record TimeRange(Instant startsAt, Instant endsAt) { }
    record ScheduledAppointment(UUID appointmentId, UUID participantId, Instant startsAt, Instant endsAt,
                                String type, String status, String locationMode, String location, String shortPurpose,
                                boolean current, List<String> availableActions, long version) { }
}
