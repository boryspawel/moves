package com.motionecosystem.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.participant.api.ParticipantClientPort.AccessLink;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantClientServiceTest {

    private final ParticipantAccessLinkRepository links = mock(ParticipantAccessLinkRepository.class);
    private final ParticipantClientService service = new ParticipantClientService(mock(ParticipantRecordRepository.class), links);

    @Test
    void returnsEmptyWhenParticipantHasNoAccessLink() {
        UUID participantId = UUID.randomUUID();

        when(links.findByParticipantId(participantId)).thenReturn(Optional.empty());

        assertThat(service.findAccessLink(participantId)).isEmpty();
    }

    @Test
    void mapsLinkLifecycleToAccessStatusStrings() {
        UUID participantId = UUID.randomUUID();

        assertThat(accessLink(participantId, ParticipantAccessLink.Status.CLAIMED).accessStatus()).isEqualTo("CLAIMED");
        assertThat(accessLink(participantId, ParticipantAccessLink.Status.ACTIVE).accessStatus()).isEqualTo("ACTIVE");
        assertThat(accessLink(participantId, ParticipantAccessLink.Status.SUSPENDED).accessStatus()).isEqualTo("SUSPENDED");
    }

    private AccessLink accessLink(UUID participantId, ParticipantAccessLink.Status status) {
        ParticipantAccessLink link = mock(ParticipantAccessLink.class);
        UUID principalAccountId = UUID.randomUUID();
        when(link.principalAccountId()).thenReturn(principalAccountId);
        when(link.accessStatus()).thenReturn(status);
        when(links.findByParticipantId(participantId)).thenReturn(Optional.of(link));
        AccessLink accessLink = service.findAccessLink(participantId).orElseThrow();
        assertThat(accessLink.principalAccountId()).isEqualTo(principalAccountId);
        return accessLink;
    }
}
