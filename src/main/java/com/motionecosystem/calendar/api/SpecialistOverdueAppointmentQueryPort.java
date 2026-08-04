package com.motionecosystem.calendar.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only calendar projection for specialist operational work. */
public interface SpecialistOverdueAppointmentQueryPort {
    List<OverdueAppointment> overdueOutcomeAppointments(UUID specialistAccountId, Set<UUID> activeParticipantIds, Instant now);

    record OverdueAppointment(UUID appointmentId, UUID participantId, Instant endsAt, String type, String status,
                              UUID latestEventId) { }
}
