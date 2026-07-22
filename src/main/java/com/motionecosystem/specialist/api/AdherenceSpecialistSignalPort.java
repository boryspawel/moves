package com.motionecosystem.specialist.api;

import java.util.UUID;

/** Creates a neutral, deduplicated request for human contact from adherence. */
public interface AdherenceSpecialistSignalPort {
    void signalContact(UUID participantAccountId, UUID barrierReportId, String category, boolean prompt);
}
