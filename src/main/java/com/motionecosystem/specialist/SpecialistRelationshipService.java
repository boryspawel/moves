package com.motionecosystem.specialist;

import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SpecialistRelationshipService {

    private final ParticipantSpecialistRelationshipRepository relationships;
    private final CurrentAccountService accounts;

    public void requireActive(UUID specialistAccountId, UUID participantId) {
        if (!relationships.existsBySpecialistAccountIdAndParticipantIdAndStatus(
                specialistAccountId, participantId, ParticipantSpecialistRelationship.Status.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "active participant-specialist relationship is required");
        }
    }

    public java.util.Set<UUID> activeParticipantIds(UUID specialistAccountId) {
        return relationships.findBySpecialistAccountIdAndStatus(specialistAccountId,
                        ParticipantSpecialistRelationship.Status.ACTIVE).stream()
                .map(SpecialistRelationshipService::participantId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Minimal selector projection; account identifiers remain an implementation detail of the client. */
    public List<ActiveParticipantView> activeParticipants(String subject) {
        UUID specialist = accounts.requireActive(subject).id();
        List<UUID> participants = relationships.findBySpecialistAccountIdAndStatus(specialist, ParticipantSpecialistRelationship.Status.ACTIVE)
                .stream()
                .map(SpecialistRelationshipService::participantId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        return IntStream.range(0, participants.size())
                .mapToObj(index -> new ActiveParticipantView(participants.get(index), "Uczestnik " + (index + 1)))
                .toList();
    }

    private static UUID participantId(ParticipantSpecialistRelationship relationship) {
        return relationship.participantId();
    }

    public record ActiveParticipantView(UUID participantId, String label) { }
}
