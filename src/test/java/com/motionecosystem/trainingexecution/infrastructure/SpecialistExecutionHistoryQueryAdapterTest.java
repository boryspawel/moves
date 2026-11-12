package com.motionecosystem.trainingexecution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.trainingexecution.api.SpecialistExecutionHistoryQueryPort.ExecutionFact;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpecialistExecutionHistoryQueryAdapterTest {
    @Test
    void returns_no_execution_facts_when_adherence_capability_is_denied() {
        var entityManager = mock(jakarta.persistence.EntityManager.class);
        var accounts = mock(CurrentAccountService.class);
        var authorization = mock(SpecialistAuthorizationPort.class);
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "execution consent denied"))
                .when(authorization).requireCapabilities(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var queries = new SpecialistExecutionHistoryQueryAdapter(entityManager, accounts, authorization);

        assertThat(queries.executionCandidates("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER),
                Instant.parse("2030-01-01T00:00:00Z"), Instant.parse("2030-02-01T00:00:00Z"))).isEmpty();
        assertThat(Arrays.stream(ExecutionFact.class.getRecordComponents()).map(component -> component.getName()))
                .contains("recordingSource", "recorderAccountId", "resultCount")
                .doesNotContain("note", "report", "results");

        verifyNoInteractions(entityManager);
    }

    @Test
    void propagates_non_authorization_failures() {
        var entityManager = mock(jakarta.persistence.EntityManager.class);
        var accounts = mock(CurrentAccountService.class);
        var authorization = mock(SpecialistAuthorizationPort.class);
        when(accounts.requireActive("specialist")).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "account state unavailable"));
        var queries = new SpecialistExecutionHistoryQueryAdapter(entityManager, accounts, authorization);

        assertThatThrownBy(() -> queries.executionCandidates("specialist", UUID.randomUUID(),
                new ActingContext(ProfessionalRole.TRAINER), Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-02-01T00:00:00Z")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("account state unavailable");
    }
}
