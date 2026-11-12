package com.motionecosystem.trainingplanning.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
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
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpecialistPlanningHistoryQueryAdapterTest {
    @Test
    void returns_no_planning_facts_when_owner_capability_is_denied() {
        EntityManager entityManager = mock(EntityManager.class);
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistAuthorizationPort authorization = mock(SpecialistAuthorizationPort.class);
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        when(accounts.requireActive("specialist"))
                .thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "consent denied"))
                .when(authorization).requireCapabilities(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        SpecialistPlanningHistoryQueryAdapter queries = new SpecialistPlanningHistoryQueryAdapter(entityManager, accounts, authorization);

        assertThat(queries.sessionCandidates("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER),
                LocalDate.parse("2030-01-01"), LocalDate.parse("2030-02-01"))).isEmpty();

        verifyNoInteractions(entityManager);
    }
}
