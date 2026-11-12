package com.motionecosystem.participantmeasurements.api;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owner-authorized, neutral participant measurement facts for encounter history. */
public interface SpecialistMeasurementHistoryQueryPort {
    List<MeasurementFact> measurementCandidates(String subject, UUID participantId, ActingContext context,
                                                Instant fromInclusive, Instant toExclusive);

    List<MeasurementFact> measurementsByAppointmentIds(String subject, UUID participantId, ActingContext context,
                                                        Set<UUID> appointmentIds);

    List<MeasurementFact> measurementsByIds(String subject, UUID participantId, ActingContext context,
                                             Set<UUID> measurementIds);

    Set<UUID> appointmentIdsWithMeasurements(String subject, UUID participantId, ActingContext context,
                                              Set<UUID> appointmentIds);

    record MeasurementFact(UUID id, UUID participantId, UUID appointmentId, String metricCode, BigDecimal value,
                           String unit, String measurementMethod, Instant measuredAt, Instant recordedAt,
                           String source) { }
}
