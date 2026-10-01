package com.motionecosystem.participant;

import com.motionecosystem.participant.api.ParticipantRecordLockPort;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class ParticipantRecordLockService implements ParticipantRecordLockPort {
    private final ParticipantRecordRepository records;

    ParticipantRecordLockService(ParticipantRecordRepository records) {
        this.records = records;
    }

    @Override
    public void lockExistingParticipant(UUID participantId) {
        records.lockById(participantId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "participant not found"));
    }
}
