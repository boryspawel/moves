package com.motionecosystem.trainingexecution.api;

import java.util.UUID;

/** Cross-module guard which prevents recovery rules from being bypassed by a direct start request. */
public interface SessionStartAuthorizationPort {
    void authorize(UUID participantAccountId, UUID plannedSessionId, String selectedVariantType);
}
