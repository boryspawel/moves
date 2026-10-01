package com.motionecosystem.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.calendar.api.SpecialistAppointmentExecutionContextPort;
import com.motionecosystem.participant.api.ParticipantMetricCatalog.PresetId;
import com.motionecosystem.participantgoals.api.MeasurementGoalProjectionPort;
import com.motionecosystem.participantmeasurements.ParticipantMeasurementService;
import com.motionecosystem.participantmeasurements.SpecialistParticipantMeasurementApplicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpecialistParticipantMeasurementApplicationServiceTest {
    @Test
    void validatesAppointmentParticipantAndInProgressStatusBeforeCreatingBoundMeasurement() {
        ParticipantMeasurementService measurements = mock(ParticipantMeasurementService.class);
        MeasurementGoalProjectionPort goals = mock(MeasurementGoalProjectionPort.class);
        SpecialistAppointmentExecutionContextPort appointments = mock(SpecialistAppointmentExecutionContextPort.class);
        SpecialistParticipantMeasurementApplicationService service = new SpecialistParticipantMeasurementApplicationService(measurements, goals, appointments);
        UUID participantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        var command = new ParticipantMeasurementService.ParticipantMeasurementCommand(PresetId.BODY_WEIGHT, new BigDecimal("75"), null,
                Instant.parse("2030-06-10T12:00:00Z"), null, null, null, null, null, appointmentId);
        UUID measurementId = UUID.randomUUID();
        var view = new ParticipantMeasurementService.ParticipantMeasurementView(measurementId, participantId, appointmentId, "body-weight",
                new BigDecimal("75"), "kg", "body-weight", command.measuredAt(), null, UUID.randomUUID(), command.measuredAt(), "APPOINTMENT_CLOSEOUT");
        when(measurements.record(eq("specialist"), eq(participantId), eq("appointment-measurement"), eq(command), any(Runnable.class)))
                .thenAnswer(call -> {
                    call.<Runnable>getArgument(4).run();
                    return new ParticipantMeasurementService.RecordResult(view, true);
                });
        when(appointments.readCloseoutContext("specialist", appointmentId)).thenReturn(
                new SpecialistAppointmentExecutionContextPort.CloseoutAppointmentContext(appointmentId, UUID.randomUUID(), participantId,
                        null, "IN_PROGRESS", 2L, command.measuredAt(), command.measuredAt().plusSeconds(3600), "Kontrola"));

        assertThat(service.record("specialist", participantId, "appointment-measurement", command).appointmentId()).isEqualTo(appointmentId);
        verify(goals).project(eq(measurementId), any(), eq(participantId), eq("body-weight"), eq(new BigDecimal("75")),
                eq("kg"), eq("body-weight"), eq(command.measuredAt()), eq(command.measuredAt()));

        when(appointments.readCloseoutContext("specialist", appointmentId)).thenReturn(
                new SpecialistAppointmentExecutionContextPort.CloseoutAppointmentContext(appointmentId, UUID.randomUUID(), UUID.randomUUID(),
                        null, "COMPLETED", 2L, command.measuredAt(), command.measuredAt().plusSeconds(3600), "Kontrola"));
        assertThatThrownBy(() -> service.record("specialist", participantId, "appointment-measurement", command)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
