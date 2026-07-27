package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Purpose;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpecialistWorklistServiceTest {

    @Test
    void listOmitsItemsForbiddenForTheSpecialist() {
        var fixture = fixture(HttpStatus.FORBIDDEN);

        assertThat(fixture.service.list("specialist", trainer(), Purpose.PERFORMANCE_PLANNING)).isEmpty();
    }

    @Test
    void listPropagatesNonForbiddenAuthorizationErrors() {
        var fixture = fixture(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> fixture.service.list("specialist", trainer(), Purpose.PERFORMANCE_PLANNING))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void participantIssueUsesTheLinkedParticipantIdRatherThanTheAccountId() {
        UUID accountId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        var accounts = mock(CurrentAccountService.class);
        var participants = mock(ParticipantClientPort.class);
        var items = mock(SpecialistWorklistItemRepository.class);
        var issues = mock(ParticipantIssueRepository.class);
        when(accounts.requireActive("participant"))
                .thenReturn(new CurrentAccount(accountId, "participant", ProfileType.PARTICIPANT));
        when(participants.findParticipantIdByPrincipalAccountId(accountId)).thenReturn(java.util.Optional.of(participantId));
        when(items.findByDeduplicationKeyAndStatusIn(any(), any())).thenReturn(java.util.Optional.empty());
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(issues.findByWorklistItemId(any())).thenReturn(java.util.Optional.empty());
        when(issues.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new SpecialistWorklistService(items, issues, mock(ParticipantIssueReplyRepository.class),
                mock(ParticipantSpecialistRelationshipRepository.class), accounts, participants,
                mock(SpecialistAuthorizationPort.class), mock(AuditRecorder.class),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), mock(AdherenceMetricsService.class));

        var result = service.reportIssue("participant",
                new SpecialistWorklistService.ParticipantIssueCommand("PAIN", "knee hurts"));

        assertThat(result.participantId()).isEqualTo(participantId);
        assertThat(result.participantId()).isNotEqualTo(accountId);
    }

    private Fixture fixture(HttpStatus authorizationStatus) {
        UUID specialist = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        var accounts = mock(CurrentAccountService.class);
        var participants = mock(ParticipantClientPort.class);
        var relationships = mock(ParticipantSpecialistRelationshipRepository.class);
        var items = mock(SpecialistWorklistItemRepository.class);
        var authorization = mock(SpecialistAuthorizationPort.class);
        var relationship = mock(ParticipantSpecialistRelationship.class);
        var item = new SpecialistWorklistItem(participant, null, "PARTICIPANT_ISSUE", "MEDIUM", "PAIN",
                "participant-reported-problem", "PARTICIPANT_ISSUE_V1", "key", Instant.EPOCH);
        when(accounts.requireActive("specialist"))
                .thenReturn(new CurrentAccount(specialist, "specialist", ProfileType.SPECIALIST));
        when(relationship.participantId()).thenReturn(participant);
        when(relationships.findBySpecialistAccountIdAndStatus(specialist, ParticipantSpecialistRelationship.Status.ACTIVE))
                .thenReturn(List.of(relationship));
        when(items.findByParticipantIdOrderByUpdatedAtDesc(participant)).thenReturn(List.of(item));
        doThrow(new ResponseStatusException(authorizationStatus, "authorization failure"))
                .when(authorization).requireCapabilities(any(), any(), any(), any(), any());
        return new Fixture(new SpecialistWorklistService(items, mock(ParticipantIssueRepository.class),
                mock(ParticipantIssueReplyRepository.class), relationships, accounts, participants, authorization, mock(AuditRecorder.class),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), mock(AdherenceMetricsService.class)));
    }

    private ActingContext trainer() {
        return new ActingContext(ProfessionalRole.TRAINER);
    }

    private record Fixture(SpecialistWorklistService service) {
    }
}
