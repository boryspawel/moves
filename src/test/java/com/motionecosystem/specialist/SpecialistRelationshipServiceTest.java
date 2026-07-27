package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialistRelationshipServiceTest {

    @Test
    void selectsOnlyCanonicalParticipantIds() {
        UUID specialistId = id(10);
        UUID accountFreeParticipantId = id(1);
        UUID canonicalParticipantId = id(3);
        var relationships = mock(ParticipantSpecialistRelationshipRepository.class);
        var accounts = mock(CurrentAccountService.class);
        var service = new SpecialistRelationshipService(relationships, accounts);
        List<ParticipantSpecialistRelationship> activeRelationships = List.of(
                relationship(accountFreeParticipantId, null),
                relationship(null, id(2)),
                relationship(canonicalParticipantId, id(99)),
                relationship(null, null));
        when(relationships.findBySpecialistAccountIdAndStatus(specialistId,
                ParticipantSpecialistRelationship.Status.ACTIVE)).thenReturn(activeRelationships);
        when(accounts.requireActive("specialist"))
                .thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));

        assertThat(service.activeParticipantIds(specialistId))
                .containsExactlyInAnyOrder(accountFreeParticipantId, canonicalParticipantId);
        assertThat(service.activeParticipants("specialist"))
                .extracting(SpecialistRelationshipService.ActiveParticipantView::participantId)
                .containsExactly(accountFreeParticipantId, canonicalParticipantId);
    }

    private static ParticipantSpecialistRelationship relationship(UUID participantId, UUID participantAccountId) {
        ParticipantSpecialistRelationship relationship = new ParticipantSpecialistRelationship(id(20), participantId, null, Instant.EPOCH);
        if (participantAccountId != null) {
            set(relationship, "participantAccountId", participantAccountId);
        }
        return relationship;
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to create relationship fixture", failure);
        }
    }
}
