package com.motionecosystem.trainingplanning.api;

import java.util.UUID;
import java.util.Optional;

/** Authorizes and validates a V2 planned session before calendar links its UUID. */
public interface AppointmentSessionLinkPort {
    void requireLinkable(String specialistSubject, UUID participantId, UUID plannedSessionId);

    Optional<AppointmentSessionContext> findAuthorizedContext(String specialistSubject, UUID participantId, UUID plannedSessionId);

    record AppointmentSessionContext(UUID sessionId, String title, UUID planId, UUID revisionId) { }
}
