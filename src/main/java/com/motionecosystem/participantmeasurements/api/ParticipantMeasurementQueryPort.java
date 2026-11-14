package com.motionecosystem.participantmeasurements.api;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Neutral read boundary for append-only participant measurement facts. */
public interface ParticipantMeasurementQueryPort {
    List<MeasurementSnapshot> recent(String subject, UUID participantId, ActingContext context, int limit);
    List<MeasurementSnapshot> timeline(String subject, UUID participantId, ActingContext context,
                                       Instant fromInclusive, Instant toExclusive, int limit);
    List<MeasurementSnapshot> forAppointment(String subject, UUID participantId, ActingContext context, UUID appointmentId, int limit);
    Optional<MeasurementSnapshot> find(String subject, UUID participantId, ActingContext context, UUID measurementId);

    record MeasurementSnapshot(UUID id, UUID participantId, UUID appointmentId, String metricCode, BigDecimal value, String unit,
                               String measurementMethod, Instant measuredAt, String note, UUID recordedByAccountId,
                               Instant recordedAt, String source) { }
}
