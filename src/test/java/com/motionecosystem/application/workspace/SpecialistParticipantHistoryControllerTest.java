package com.motionecosystem.application.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SpecialistParticipantHistoryControllerTest {
    @Test
    void forwardsHistoryFiltersAndActingContextToTheApplicationService() {
        SpecialistParticipantHistoryService history = mock(SpecialistParticipantHistoryService.class);
        SpecialistParticipantHistoryController controller = new SpecialistParticipantHistoryController(history);
        UUID participant = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("specialist").build();
        var page = new SpecialistParticipantHistoryService.HistoryPage(List.of(), null);
        when(history.history(eq("specialist"), eq(participant), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(page);

        var result = controller.get(jwt, participant, ProfessionalRole.TRAINER, Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-02-01T00:00:00Z"), "appointment,measurement", "cursor", 25);

        assertThat(result).isSameAs(page);
        verify(history).history(eq("specialist"), eq(participant),
                eq(new com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext(ProfessionalRole.TRAINER)),
                eq(new SpecialistParticipantHistoryService.HistoryQuery(Instant.parse("2030-01-01T00:00:00Z"),
                        Instant.parse("2030-02-01T00:00:00Z"),
                        java.util.Set.of(SpecialistParticipantHistoryService.FilterKind.APPOINTMENT,
                                SpecialistParticipantHistoryService.FilterKind.MEASUREMENT), 25, "cursor")));
    }

    @Test
    void forwardsResolverRequestWithExplicitActingContext() {
        SpecialistParticipantHistoryService history = mock(SpecialistParticipantHistoryService.class);
        SpecialistParticipantHistoryController controller = new SpecialistParticipantHistoryController(history);
        UUID participant = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("specialist").build();
        var resolution = new SpecialistParticipantHistoryService.HistoryResolution(null, null, null, null);
        when(history.resolve(eq("specialist"), eq(participant), org.mockito.ArgumentMatchers.any(), eq("encounter:" + participant))).thenReturn(resolution);

        assertThat(controller.resolve(jwt, participant, ProfessionalRole.TRAINER, "encounter:" + participant)).isSameAs(resolution);
        verify(history).resolve("specialist", participant,
                new com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext(ProfessionalRole.TRAINER), "encounter:" + participant);
    }
}
