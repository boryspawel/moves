package com.motionecosystem.trainingexecution.api;

import com.motionecosystem.trainingexecution.SessionExecutionService;
import java.util.UUID;

/** Explicit specialist-owned recording entry point; appointment context supplies participant and session. */
public interface SpecialistSessionExecutionPort {
    SessionExecutionService.ExecutionView record(String subject, UUID participantId, UUID plannedSessionId,
                                                  String idempotencyKey, SessionExecutionService.DeclareExecutionCommand command);
}
