package com.motionecosystem.application.workspace;

import com.motionecosystem.calendar.api.SpecialistAppointmentExecutionContextPort;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.participantmeasurements.api.ParticipantMeasurementQueryPort;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence;
import com.motionecosystem.participantdocumentation.api.ParticipantAppointmentSummaryQueryPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import io.swagger.v3.oas.annotations.Operation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/specialist/appointments/{appointmentId}/closeout-context")
class SpecialistAppointmentCloseoutController {
    private final SpecialistAppointmentExecutionContextPort appointments;
    private final ParticipantClientPort participants;
    private final ParticipantMeasurementQueryPort measurements;
    private final SessionExecutionPersistence executions;
    private final ParticipantAppointmentSummaryQueryPort summaries;
    SpecialistAppointmentCloseoutController(SpecialistAppointmentExecutionContextPort appointments, ParticipantClientPort participants,
            ParticipantMeasurementQueryPort measurements, SessionExecutionPersistence executions, ParticipantAppointmentSummaryQueryPort summaries) { this.appointments=appointments;this.participants=participants;this.measurements=measurements;this.executions=executions;this.summaries=summaries; }
    @GetMapping @Operation(operationId="getSpecialistAppointmentCloseoutContext")
    CloseoutContext get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID appointmentId,@RequestParam ProfessionalRole actingContext) {
        var appointment=appointments.readCloseoutContext(jwt.getSubject(),appointmentId);
        var execution=appointment.plannedSessionId()==null?null:executions.findByPlannedSessionId(appointment.plannedSessionId()).orElse(null);
        boolean required=appointment.plannedSessionId()!=null; boolean recorded=execution!=null;
        String name=participants.find(appointment.participantId()).map(ParticipantClientPort.ClientRecord::displayName).orElse("Uczestnik");
        return new CloseoutContext(appointment.appointmentId(),appointment.participantId(),name,appointment.status(),appointment.version(),appointment.startsAt(),appointment.endsAt(),appointment.shortPurpose(),required,recorded,
                execution==null?null:new ExecutionSummary(execution.execution().id(),execution.execution().outcome(),execution.execution().recordedAt()),
                measurements.forAppointment(appointment.participantId(),appointment.appointmentId(),50).stream().map(item->new MeasurementFact(item.id(),item.metricCode(),item.value(),item.unit(),item.measuredAt())).toList(),
                summaries.findAppointmentSummary(jwt.getSubject(),appointment.participantId(),appointment.appointmentId(),new ActingContext(actingContext)).map(item->new SummaryNote(item.noteId(),item.title(),item.content(),item.recordedAt())).orElse(null),
                "IN_PROGRESS".equals(appointment.status())&&(!required||recorded));
    }
    record CloseoutContext(UUID appointmentId,UUID participantId,String participantName,String status,long version,Instant startsAt,Instant endsAt,String shortPurpose,boolean executionRequired,boolean executionRecorded,ExecutionSummary execution,List<MeasurementFact> measurements,SummaryNote summaryNote,boolean canComplete){}
    record ExecutionSummary(UUID executionId,String outcome,Instant recordedAt){}
    record MeasurementFact(UUID measurementId,String metricCode,BigDecimal value,String unit,Instant measuredAt){}
    record SummaryNote(UUID noteId,String title,String content,Instant recordedAt){}
}
