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

    /** Active operational state is deliberately queried separately from a selected calendar day. */
    List<OperationalAppointment> inProgress(UUID specialistAccountId, Set<UUID> activeParticipantIds, Instant now);

    List<BlockingTimeRange> blockingInRange(UUID specialistAccountId, Instant fromInclusive, Instant toExclusive);

    record AppointmentSummary(UUID appointmentId, Instant startsAt, Instant endsAt, String type,
                              String status, String shortPurpose, UUID plannedSessionId, List<String> availableActions,
                              long version, Instant recordedAt, Instant updatedAt) {
        public AppointmentSummary(UUID appointmentId, Instant startsAt, Instant endsAt, String type,
                                  String status, String shortPurpose, Instant recordedAt, Instant updatedAt) {
            this(appointmentId, startsAt, endsAt, type, status, shortPurpose, null, List.of(), 0, recordedAt, updatedAt);
        }
    }

    record OperationalAppointment(UUID appointmentId, UUID participantId, Instant startsAt, Instant endsAt,
                                  String type, String status, String locationMode, String location,
                                  String shortPurpose, UUID plannedSessionId, boolean current, List<String> availableActions, long version) {
        public OperationalAppointment(UUID appointmentId, UUID participantId, Instant startsAt, Instant endsAt,
                                      String type, String status, String locationMode, String location,
                                      String shortPurpose, boolean current, List<String> availableActions, long version) {
            this(appointmentId, participantId, startsAt, endsAt, type, status, locationMode, location,
                    shortPurpose, null, current, availableActions, version);
        }
    }

    record BlockingTimeRange(Instant startsAt, Instant endsAt) { }
}
