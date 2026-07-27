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

    private Fixture fixture(HttpStatus authorizationStatus) {
        UUID specialist = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        var accounts = mock(CurrentAccountService.class);
        var relationships = mock(ParticipantSpecialistRelationshipRepository.class);
        var items = mock(SpecialistWorklistItemRepository.class);
        var authorization = mock(SpecialistAuthorizationPort.class);
        var relationship = mock(ParticipantSpecialistRelationship.class);
        var item = new SpecialistWorklistItem(participant, null, "PARTICIPANT_ISSUE", "MEDIUM", "PAIN",
                "participant-reported-problem", "PARTICIPANT_ISSUE_V1", "key", Instant.EPOCH);
        when(accounts.requireActive("specialist"))
                .thenReturn(new CurrentAccount(specialist, "specialist", ProfileType.SPECIALIST));
        when(relationship.participantAccountId()).thenReturn(participant);
        when(relationships.findBySpecialistAccountIdAndStatus(specialist, ParticipantSpecialistRelationship.Status.ACTIVE))
                .thenReturn(List.of(relationship));
        when(items.findByParticipantAccountIdOrderByUpdatedAtDesc(participant)).thenReturn(List.of(item));
        doThrow(new ResponseStatusException(authorizationStatus, "authorization failure"))
                .when(authorization).requireCapabilities(any(), any(), any(), any(), any());
        return new Fixture(new SpecialistWorklistService(items, mock(ParticipantIssueRepository.class),
                mock(ParticipantIssueReplyRepository.class), relationships, accounts, authorization, mock(AuditRecorder.class),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), mock(AdherenceMetricsService.class)));
    }

    private ActingContext trainer() {
        return new ActingContext(ProfessionalRole.TRAINER);
    }

    private record Fixture(SpecialistWorklistService service) {
    }
}
