package com.motionecosystem.participantmeasurements;

import com.motionecosystem.calendar.api.SpecialistAppointmentExecutionContextPort;
import com.motionecosystem.participantgoals.api.MeasurementGoalProjectionPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates one participant fact with its optional, derived goal-progress projections. */
@Service
public class SpecialistParticipantMeasurementApplicationService {
    private final ParticipantMeasurementService measurements;
    private final MeasurementGoalProjectionPort goalProjection;
    private final SpecialistAppointmentExecutionContextPort appointments;

    public SpecialistParticipantMeasurementApplicationService(ParticipantMeasurementService measurements,
            MeasurementGoalProjectionPort goalProjection, SpecialistAppointmentExecutionContextPort appointments) {
        this.measurements = measurements;
        this.goalProjection = goalProjection;
        this.appointments = appointments;
    }

    @Transactional
    public ParticipantMeasurementService.ParticipantMeasurementView record(String subject, UUID participantId, String idempotencyKey,
            ParticipantMeasurementService.ParticipantMeasurementCommand command) {
        ParticipantMeasurementService.RecordResult result = measurements.record(subject, participantId, idempotencyKey, command,
                () -> validateAppointment(subject, participantId, command.appointmentId()));
        if (result.created()) {
            ParticipantMeasurementService.ParticipantMeasurementView measurement = result.measurement();
            goalProjection.project(measurement.id(), measurement.recordedByAccountId(), measurement.participantId(),
                    measurement.metricCode(), measurement.value(), measurement.unit(), measurement.measurementMethod(),
                    measurement.measuredAt(), measurement.recordedAt());
        }
        return result.measurement();
    }

    private void validateAppointment(String subject, UUID participantId, UUID appointmentId) {
        if (appointmentId == null) return;
        var appointment = appointments.readCloseoutContext(subject, appointmentId);
        if (!participantId.equals(appointment.participantId()) || !"IN_PROGRESS".equals(appointment.status()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "appointment must be in progress for this participant");
    }
}
