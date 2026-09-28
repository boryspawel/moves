package com.motionecosystem.application.workspace;

import com.motionecosystem.calendar.api.AppointmentPlannedSessionValidationPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort;
import com.motionecosystem.trainingplanning.api.AppointmentSessionLinkPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Composes planning eligibility and execution state inside Calendar's write transaction. */
@Component
@RequiredArgsConstructor
class AppointmentPlannedSessionValidationAdapter implements AppointmentPlannedSessionValidationPort {
    private final AppointmentSessionLinkPort planning;
    private final SessionExecutionProgressQueryPort progress;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireLinkable(String subject, UUID participantId, UUID plannedSessionId) {
        planning.requireLinkable(subject, participantId, plannedSessionId);
        var execution = progress.findForSessions(participantId, List.of(plannedSessionId)).get(plannedSessionId);
        if (execution != null && (execution.state() == SessionExecutionProgressQueryPort.ExecutionState.IN_PROGRESS
                || execution.state() == SessionExecutionProgressQueryPort.ExecutionState.PAUSED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "planned session has an active participant execution");
        }
    }
}
