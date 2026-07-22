package com.motionecosystem.participant.api;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/** Minimal participant context exported to read-model composition. */
public interface ParticipantContextQueryPort {

    Optional<ParticipantContext> findContext(UUID participantAccountId);

    record ParticipantContext(UUID participantAccountId, ZoneId timeZone) { }
}
