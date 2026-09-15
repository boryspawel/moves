package com.motionecosystem.adherence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.adherence.api.AdherenceSummary;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdherenceSummaryControllerTest {
    @Test
    void resolvesOnlyTheSignedInParticipantsCanonicalAccessLink() {
        UUID accountId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        ParticipantClientPort participants = mock(ParticipantClientPort.class);
        AdherenceSummaryService summaries = mock(AdherenceSummaryService.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("participant");
        when(accounts.requireActive("participant")).thenReturn(new CurrentAccount(accountId, "participant", ProfileType.PARTICIPANT));
        when(participants.findParticipantIdByPrincipalAccountId(accountId)).thenReturn(Optional.of(participantId));
        when(summaries.summarize(eq(participantId), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 15))))
                .thenReturn(AdherenceSummary.noData());

        new AdherenceSummaryController(accounts, participants, summaries).summary(jwt,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15));

        verify(summaries).summarize(participantId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15));
    }

    @Test
    void rejectsAccountWithoutParticipantProfileBeforeSummaryLookup() {
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("specialist");
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(UUID.randomUUID(), "specialist", ProfileType.SPECIALIST));

        assertThatThrownBy(() -> new AdherenceSummaryController(accounts, mock(ParticipantClientPort.class), mock(AdherenceSummaryService.class))
                .summary(jwt, null, null))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
    }
}
