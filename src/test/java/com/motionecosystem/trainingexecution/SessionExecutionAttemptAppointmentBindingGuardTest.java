package com.motionecosystem.trainingexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.calendar.api.AppointmentSessionBindingQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import com.motionecosystem.trainingexecution.api.ExecutionAdherencePort;
import com.motionecosystem.trainingexecution.api.SessionStartAuthorizationPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import com.motionecosystem.trainingplanning.api.PlannedSessionExecutionPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class SessionExecutionAttemptAppointmentBindingGuardTest {

    @Test
    void boundSessionRejectsEveryAttemptMutationBeforeItChangesAttemptState() {
        Fixture fixture = fixture(true);

        assertConflict(() -> fixture.service.start("participant", fixture.sessionId, fixture.revisionId, "STANDARD", "start"));
        assertConflict(() -> fixture.service.pause("participant", fixture.attempt.id));
        assertConflict(() -> fixture.service.resume("participant", fixture.attempt.id));
        assertConflict(() -> fixture.service.abandon("participant", fixture.attempt.id, "FATIGUE"));
        assertConflict(() -> fixture.service.updateProgress("participant", fixture.attempt.id, UUID.randomUUID(), true));
        assertConflict(() -> fixture.service.recordFact("participant", fixture.attempt.id,
                new SessionExecutionAttemptService.FactCommand(UUID.randomUUID(), "SKIPPED", "FATIGUE", null)));
        assertConflict(() -> fixture.service.finish("participant", fixture.attempt.id, "finish",
                new SessionExecutionAttemptService.FinishCommand("COMPLETE", 0, 0, null, null, null, null)));
        assertConflict(() -> fixture.service.completeAfterFinalDeclaration("participant", fixture.participantId, fixture.sessionId));

        assertThat(fixture.attempt.status).isEqualTo(SessionExecutionAttempt.Status.STARTED.name());
        verify(fixture.progress, never()).save(any());
        verify(fixture.attempts, never()).save(any());
    }

    @Test
    void unboundSessionRetainsExistingPauseAndResumeTransitions() {
        Fixture fixture = fixture(false);

        assertThat(fixture.service.pause("participant", fixture.attempt.id).state()).isEqualTo("PAUSED");
        assertThat(fixture.service.resume("participant", fixture.attempt.id).state()).isEqualTo("STARTED");
        assertThat(fixture.attempt.status).isEqualTo(SessionExecutionAttempt.Status.STARTED.name());
    }

    private static void assertConflict(ThrowingRunnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static Fixture fixture(boolean bound) {
        UUID accountId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        Instant now = Instant.EPOCH;
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        ParticipantClientPort participants = mock(ParticipantClientPort.class);
        PlannedSessionExecutionPort sessions = mock(PlannedSessionExecutionPort.class);
        SessionExecutionAttemptRepository attempts = mock(SessionExecutionAttemptRepository.class);
        SessionExecutionAttemptProgressRepository progress = mock(SessionExecutionAttemptProgressRepository.class);
        AppointmentSessionBindingQueryPort bindings = mock(AppointmentSessionBindingQueryPort.class);
        SessionSafetyDecisionQueryPort safety = mock(SessionSafetyDecisionQueryPort.class);
        SessionExecutionAttempt attempt = new SessionExecutionAttempt(participantId, sessionId, revisionId, "STANDARD", "existing", now);
        when(accounts.requireActive("participant")).thenReturn(new CurrentAccount(accountId, "participant", ProfileType.PARTICIPANT));
        when(participants.findParticipantIdByPrincipalAccountId(accountId)).thenReturn(Optional.of(participantId));
        when(sessions.lockOwnedSession(sessionId, participantId)).thenReturn(Optional.of(
                new PlannedSessionExecutionPort.PlannedSessionSnapshot(sessionId, participantId,
                        PlannedSessionExecutionPort.SessionState.ASSIGNED, List.of())));
        when(bindings.isBound(participantId, sessionId)).thenReturn(bound);
        when(safety.evaluateForSessions(participantId, revisionId, List.of(sessionId), Instant.EPOCH)).thenReturn(java.util.Map.of(sessionId,
                new SessionSafetyDecisionQueryPort.SessionSafetyDecision(sessionId,
                        SessionSafetyDecisionQueryPort.SafetyDecisionStatus.ALLOWED, UUID.randomUUID(), List.of(), Instant.EPOCH)));
        when(attempts.findById(attempt.id)).thenReturn(Optional.of(attempt));
        when(attempts.findByParticipantAccountIdAndStartIdempotencyKey(participantId, "start")).thenReturn(Optional.empty());
        SessionExecutionAttemptService service = new SessionExecutionAttemptService(accounts, participants, sessions, attempts, progress,
                mock(SessionExecutionPersistence.class), mock(SessionExecutionService.class), mock(PlanRevisionQueryPort.class),
                safety, mock(SessionStartAuthorizationPort.class), mock(ExecutionAdherencePort.class),
                mock(AdherenceMetricsService.class), mock(AuditRecorder.class), Clock.fixed(now, ZoneOffset.UTC),
                mock(SessionExecutionAttemptFactRepository.class), new ObjectMapper(), mock(EntityManager.class), bindings);
        return new Fixture(service, attempts, progress, attempt, participantId, sessionId, revisionId);
    }

    private record Fixture(SessionExecutionAttemptService service, SessionExecutionAttemptRepository attempts,
                           SessionExecutionAttemptProgressRepository progress, SessionExecutionAttempt attempt,
                           UUID participantId, UUID sessionId, UUID revisionId) { }

    @FunctionalInterface
    private interface ThrowingRunnable { void run(); }
}
