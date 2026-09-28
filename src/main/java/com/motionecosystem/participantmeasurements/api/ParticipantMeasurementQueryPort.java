package com.motionecosystem.participantmeasurements.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Neutral read boundary for append-only participant measurement facts. */
public interface ParticipantMeasurementQueryPort {
    List<MeasurementSnapshot> recent(UUID participantId, int limit);
    List<MeasurementSnapshot> timeline(UUID participantId, Instant fromInclusive, Instant toExclusive, int limit);
    List<MeasurementSnapshot> forAppointment(UUID participantId, UUID appointmentId, int limit);
    Optional<MeasurementSnapshot> find(UUID participantId, UUID measurementId);

    record MeasurementSnapshot(UUID id, UUID participantId, UUID appointmentId, String metricCode, BigDecimal value, String unit,
                               String measurementMethod, Instant measuredAt, String note, UUID recordedByAccountId,
                               Instant recordedAt, String source) { }
}
