package com.motionecosystem.application.workspace;

import com.motionecosystem.calendar.api.AppointmentExecutionStatusPort;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AppointmentExecutionStatusService implements AppointmentExecutionStatusPort {
    private final SessionExecutionPersistence executions;
    AppointmentExecutionStatusService(SessionExecutionPersistence executions) { this.executions = executions; }
    @Override @Transactional(readOnly = true)
    public boolean hasRecordedExecution(UUID plannedSessionId) {
        return plannedSessionId != null && executions.findByPlannedSessionId(plannedSessionId).isPresent();
    }
}
