package com.motionecosystem.application.workspace;

import com.motionecosystem.calendar.api.SpecialistAppointmentExecutionContextPort;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence;
import com.motionecosystem.trainingexecution.SessionExecutionService;
import com.motionecosystem.trainingexecution.api.SpecialistSessionExecutionPort;
import com.motionecosystem.trainingplanning.api.AppointmentSessionLinkPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SpecialistAppointmentExecutionService {
    private final SpecialistAppointmentExecutionContextPort appointments;
    private final ParticipantClientPort participants;
    private final AppointmentSessionLinkPort planning;
    private final PlanRevisionQueryPort revisions;
    private final ExerciseCatalogQueryPort catalog;
    private final SessionExecutionPersistence executions;
    private final SpecialistSessionExecutionPort recorder;

    @Transactional(readOnly = true)
    public SpecialistAppointmentExecutionContext context(String subject, UUID appointmentId) {
        var appointment = appointments.readExecutionContext(subject, appointmentId);
        var link = planning.findAuthorizedContext(subject, appointment.participantId(), appointment.plannedSessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "linked session is unavailable"));
        var participant = participants.find(appointment.participantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant not found"));
        var revision = revisions.findRevision(link.revisionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "linked revision is unavailable"));
        var session = revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream())
                .filter(item -> item.id().equals(appointment.plannedSessionId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "linked session is unavailable"));
        var names = catalog.findPublishedVersions(session.prescriptions().stream().map(PlanRevisionQueryPort.PrescriptionSnapshot::exerciseVersionId)
                .collect(java.util.stream.Collectors.toSet()));
        var stored = executions.findByPlannedSessionId(appointment.plannedSessionId()).orElse(null);
        return new SpecialistAppointmentExecutionContext(appointment.appointmentId(), appointment.status(), participant.id(), participant.displayName(),
                session.id(), session.title(), link.planId(), link.revisionId(), session.prescriptions().stream()
                .sorted(Comparator.comparingInt(PlanRevisionQueryPort.PrescriptionSnapshot::position))
                .map(item -> new SpecialistExecutionPrescriptionView(item.id(), item.position(), names.get(item.exerciseVersionId()).canonicalName(), item.canonicalDoseType(),
                        item.doseType(), item.sets(), item.repetitions(), item.durationSeconds(), item.distanceMeters(), item.contacts(),
                        item.externalLoadValue(), item.externalLoadUnit(), item.intensityType(), item.intensityValue(), item.intensityZone(), item.side(),
                        item.tempo(), item.restSeconds(), item.notes())).toList(),
                stored == null ? null : new SpecialistExecutionSummary(stored.execution().id(), stored.execution().outcome(), stored.execution().recordedAt()),
                stored == null && isRecordingAvailable(subject, appointment));
    }

    @Transactional
    public SessionExecutionService.ExecutionView record(String subject, UUID appointmentId, String key, SessionExecutionService.DeclareExecutionCommand command) {
        var appointment = appointments.lockWritableExecutionContext(subject, appointmentId);
        if (executions.findByPlannedSessionId(appointment.plannedSessionId()).isEmpty()) {
            planning.requireLinkable(subject, appointment.participantId(), appointment.plannedSessionId());
        }
        return recorder.record(subject, appointment.participantId(), appointment.plannedSessionId(), key, command);
    }

    boolean isRecordingAvailable(String subject, UUID appointmentId) {
        try {
            var appointment = appointments.readExecutionContext(subject, appointmentId);
            return isRecordingAvailable(subject, appointment);
        } catch (ResponseStatusException unavailable) {
            return false;
        }
    }

    boolean isExecutionRecorded(String subject, UUID appointmentId) {
        try {
            var appointment = appointments.readExecutionContext(subject, appointmentId);
            return executions.findByPlannedSessionId(appointment.plannedSessionId()).isPresent();
        } catch (ResponseStatusException unavailable) { return false; }
    }

    private boolean isRecordingAvailable(String subject, SpecialistAppointmentExecutionContextPort.AppointmentExecutionContext appointment) {
        if (!"IN_PROGRESS".equals(appointment.status()) || executions.findByPlannedSessionId(appointment.plannedSessionId()).isPresent()) return false;
        var link = planning.findAuthorizedContext(subject, appointment.participantId(), appointment.plannedSessionId()).orElse(null);
        if (link == null) return false;
        var active = revisions.findActiveRevision(appointment.participantId()).orElse(null);
        if (active == null || !active.revisionId().equals(link.revisionId()) || !"ACTIVE".equals(active.status())) return false;
        return active.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream())
                .anyMatch(session -> session.id().equals(appointment.plannedSessionId()) && "ASSIGNED".equals(session.status()));
    }

    public record SpecialistAppointmentExecutionContext(UUID appointmentId, String appointmentStatus, UUID participantId, String participantName,
                            UUID plannedSessionId, String sessionTitle, UUID planId, UUID revisionId,
                            List<SpecialistExecutionPrescriptionView> prescriptions, SpecialistExecutionSummary recordedExecution,
                            boolean recordingAllowed) { }
    public record SpecialistExecutionPrescriptionView(UUID id, int position, String exerciseName, String canonicalDoseType, String doseType,
                        Integer sets, Integer repetitions, Integer durationSeconds, BigDecimal distanceMeters, Integer contacts,
                        BigDecimal externalLoadValue, String externalLoadUnit, String intensityType, BigDecimal intensityValue,
                        String intensityZone, String side, String tempo, Integer restSeconds, String notes) { }
    public record SpecialistExecutionSummary(UUID executionId, String outcome, java.time.Instant recordedAt) { }
}
