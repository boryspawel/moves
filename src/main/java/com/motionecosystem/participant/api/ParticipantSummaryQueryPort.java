package com.motionecosystem.participant.api;

import java.util.Optional;
import java.util.UUID;

/** Minimal participant header data exported for authorized read-model composition. */
public interface ParticipantSummaryQueryPort {

    Optional<ParticipantSummary> findSummary(UUID participantAccountId);

    record ParticipantSummary(UUID participantAccountId, String displayName) { }
}
