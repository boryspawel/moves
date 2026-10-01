package com.motionecosystem.participant.api;

import java.util.UUID;

/** Serializes writes tied to a canonical participant record within the caller transaction. */
public interface ParticipantRecordLockPort {
    void lockExistingParticipant(UUID participantId);
}
