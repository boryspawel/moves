package com.motionecosystem.calendar.api;

import java.util.UUID;

/** Read-only execution fact used by calendar lifecycle rules. */
public interface AppointmentExecutionStatusPort {
    boolean hasRecordedExecution(UUID plannedSessionId);
}
