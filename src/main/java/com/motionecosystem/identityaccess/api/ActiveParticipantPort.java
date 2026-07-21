package com.motionecosystem.identityaccess.api;

import java.util.Optional;
import java.util.UUID;

/** Public identity boundary used by modules that need to validate a participant reference. */
public interface ActiveParticipantPort {

    Optional<ActiveParticipantSnapshot> findActiveParticipant(UUID accountId);

    record ActiveParticipantSnapshot(UUID accountId) {
    }
}
