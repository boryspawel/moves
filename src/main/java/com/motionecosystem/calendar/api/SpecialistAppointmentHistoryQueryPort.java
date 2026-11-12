package com.motionecosystem.calendar.api;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owner-authorized, metadata-only calendar facts for encounter-history composition. */
public interface SpecialistAppointmentHistoryQueryPort {
    List<AppointmentSnapshot> appointmentCandidates(String subject, UUID participantId, ActingContext context,
                                                    Instant fromInclusive, Instant toExclusive);

    List<AppointmentSnapshot> appointmentsByIds(String subject, UUID participantId, ActingContext context,
                                                Set<UUID> appointmentIds);

    List<AppointmentSnapshot> appointmentsByPlannedSessionIds(String subject, UUID participantId, ActingContext context,
                                                              Set<UUID> plannedSessionIds);

    List<LifecycleSnapshot> lifecycleByAppointmentIds(String subject, UUID participantId, ActingContext context,
                                                      Set<UUID> appointmentIds);

    List<LifecycleSnapshot> lifecycleByEventIds(String subject, UUID participantId, ActingContext context,
                                                Set<UUID> eventIds);

    record AppointmentSnapshot(UUID appointmentId, UUID specialistAccountId, UUID participantId,
                               Instant startsAt, Instant endsAt, String type, String status,
                               UUID plannedSessionId) { }

    record LifecycleSnapshot(UUID eventId, UUID appointmentId, Instant effectiveAt, Instant recordedAt,
                             String type, String fromStatus, String toStatus) { }
}
