package com.motionecosystem.specialist;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SpecialistRelationshipService {

    private final ParticipantSpecialistRelationshipRepository relationships;

    public void requireActive(UUID specialistAccountId, UUID participantAccountId) {
        if (!relationships.existsBySpecialistAccountIdAndParticipantAccountIdAndStatus(
                specialistAccountId, participantAccountId, ParticipantSpecialistRelationship.Status.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "active participant-specialist relationship is required");
        }
    }
}
