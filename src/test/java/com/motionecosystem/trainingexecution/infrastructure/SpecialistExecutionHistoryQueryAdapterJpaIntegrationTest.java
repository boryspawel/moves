package com.motionecosystem.trainingexecution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.domain.PrincipalAccount;
import com.motionecosystem.participant.ParticipantRecord;
import com.motionecosystem.support.PostgresTestConfiguration;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ExecutionData;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class SpecialistExecutionHistoryQueryAdapterJpaIntegrationTest {
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void returnsAuthorizedJpaExecutionFactsAndMinimalProvenanceLinks() {
        Instant recordedAt = Instant.parse("2030-01-15T12:00:00Z");
        PrincipalAccount specialistAccount = PrincipalAccount.create("history-query-specialist-" + UUID.randomUUID(), recordedAt);
        entityManager.persist(specialistAccount);
        ParticipantRecord participantRecord = new ParticipantRecord("History query participant",
                ParticipantRecord.RelationshipContext.CLIENT, null, null, ZoneOffset.UTC, specialistAccount.id(), recordedAt);
        entityManager.persist(participantRecord);
        UUID specialist = specialistAccount.id();
        UUID participant = participantRecord.id();
        UUID session = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        entityManager.persist(new SessionExecutionJpaEntity(new ExecutionData(executionId, session, participant, true,
                "history-query", recordedAt, null, "PENDING", "COMPLETED", null, null, specialist, "SPECIALIST")));
        entityManager.flush();

        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistAuthorizationPort authorization = mock(SpecialistAuthorizationPort.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialist, "specialist", ProfileType.SPECIALIST));
        SpecialistExecutionHistoryQueryAdapter adapter = new SpecialistExecutionHistoryQueryAdapter(entityManager, accounts, authorization);

        var facts = adapter.executionCandidates("specialist", participant, new ActingContext(ProfessionalRole.TRAINER),
                recordedAt.minusSeconds(1), recordedAt.plusSeconds(1));
        var links = adapter.executionLinksByPlannedSessionIds("specialist", participant,
                new ActingContext(ProfessionalRole.TRAINER), Set.of(session));
        var hydrated = adapter.executionsByPlannedSessionIds("specialist", participant,
                new ActingContext(ProfessionalRole.TRAINER), Set.of(session));

        assertThat(facts).singleElement().extracting(item -> item.id(), item -> item.plannedSessionId(),
                item -> item.recorderAccountId(), item -> item.recordingSource(), item -> item.resultCount())
                .containsExactly(executionId, session, specialist, "SPECIALIST", 0L);
        assertThat(facts).singleElement().extracting(item -> item.attemptId(), item -> item.declaredCompletion(), item -> item.outcome())
                .containsExactly(null, true, "COMPLETED");
        assertThat(links).singleElement().extracting(item -> item.id(), item -> item.participantId(),
                item -> item.plannedSessionId(), item -> item.recorderAccountId(), item -> item.recordingSource())
                .containsExactly(executionId, participant, session, specialist, "SPECIALIST");
        assertThat(hydrated).singleElement().extracting(item -> item.id(), item -> item.attemptId(),
                item -> item.recordingSource()).containsExactly(executionId, null, "SPECIALIST");
    }
}
