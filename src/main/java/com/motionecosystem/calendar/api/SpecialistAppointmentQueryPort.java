package com.motionecosystem.calendar.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded appointment projection for an already authorized specialist-participant relationship. */
public interface SpecialistAppointmentQueryPort {

    List<AppointmentSummary> findForParticipant(UUID specialistAccountId, UUID participantId,
                                                Instant fromInclusive, Instant toExclusive, int limit);

    List<OperationalAppointment> inRange(UUID specialistAccountId, Instant fromInclusive, Instant toExclusive,
                                         Set<UUID> activeParticipantIds, Instant now);

    List<BlockingTimeRange> blockingInRange(UUID specialistAccountId, Instant fromInclusive, Instant toExclusive);

    record AppointmentSummary(UUID appointmentId, Instant startsAt, Instant endsAt, String type,
                              String status, String shortPurpose, Instant recordedAt, Instant updatedAt) { }

    record OperationalAppointment(UUID appointmentId, UUID participantId, Instant startsAt, Instant endsAt,
                                  String type, String status, String locationMode, String location,
                                  String shortPurpose, boolean current, List<String> availableActions, long version) { }

    record BlockingTimeRange(Instant startsAt, Instant endsAt) { }
}
