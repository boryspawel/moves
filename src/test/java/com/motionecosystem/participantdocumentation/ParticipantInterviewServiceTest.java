package com.motionecosystem.participantdocumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ParticipantInterviewServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Test
    void snapshotsTemplateRequiresAllRequiredAnswersAndMakesCompletedInterviewImmutable() {
        Fixture fixture = fixture();
        ParticipantInterview interview = persistedInterview(fixture);
        when(fixture.responses.findByInterviewId(interview.id)).thenReturn(List.of());

        var started = fixture.service.start("specialist", fixture.participantId, trainer(), "start-key");

        assertThat(started).extracting(ParticipantDocumentationService.InterviewView::templateCode,
                ParticipantDocumentationService.InterviewView::templateVersion)
                .containsExactly("INITIAL_SPECIALIST_INTERVIEW", 1);
        assertThat(started.questions()).containsExactlyElementsOf(ParticipantDocumentationService.TEMPLATE);
        assertStatus(HttpStatus.BAD_REQUEST, () -> fixture.service.complete("specialist", fixture.participantId,
                interview.id, trainer(), "complete-missing", 0));

        List<InterviewResponse> answers = requiredResponses(interview.id);
        when(fixture.responses.findByInterviewId(interview.id)).thenReturn(answers);
        var completed = fixture.service.complete("specialist", fixture.participantId, interview.id, trainer(), "complete-key", 0);

        assertThat(completed.status()).isEqualTo(ParticipantInterview.Status.COMPLETED);
        assertThat(completed.availableActions()).containsExactly("START_NEXT_INTERVIEW");
        assertStatus(HttpStatus.CONFLICT, () -> fixture.service.save("specialist", fixture.participantId, interview.id,
                trainer(), 0, List.of()));
        verify(fixture.responses, never()).deleteByInterviewId(interview.id);
    }

    @Test
    void completionSupersedesEarlierCompletedInterviewAndRejectsStaleVersion() {
        Fixture fixture = fixture();
        ParticipantInterview current = persistedInterview(fixture);
        ParticipantInterview earlier = new ParticipantInterview(fixture.participantId, fixture.specialistId, NOW.minusSeconds(1));
        earlier.status = ParticipantInterview.Status.COMPLETED;
        when(fixture.responses.findByInterviewId(current.id)).thenReturn(requiredResponses(current.id));
        when(fixture.interviews.findByParticipantIdAndSpecialistIdOrderByCreatedAtDesc(fixture.participantId, fixture.specialistId))
                .thenReturn(List.of(current, earlier));

        fixture.service.complete("specialist", fixture.participantId, current.id, trainer(), "complete-key", 0);

        assertThat(earlier.status).isEqualTo(ParticipantInterview.Status.SUPERSEDED);
        current.version = 1;
        assertStatus(HttpStatus.CONFLICT, () -> fixture.service.save("specialist", fixture.participantId, current.id,
                trainer(), 0, List.of()));
    }

    @Test
    void scopesInterviewToActiveSpecialistRelationAndReplaysStartWithoutCreatingAnotherRecord() {
        Fixture fixture = fixture();
        UUID foreignInterview = UUID.randomUUID();
        when(fixture.interviews.findByIdAndParticipantIdAndSpecialistId(foreignInterview, fixture.participantId, fixture.specialistId))
                .thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> fixture.service.get("specialist", fixture.participantId, foreignInterview, trainer()));
        verify(fixture.relationships).requireActive(fixture.specialistId, fixture.participantId);

        ParticipantInterview existing = new ParticipantInterview(fixture.participantId, fixture.specialistId, NOW);
        RecordIdempotency replay = new RecordIdempotency(fixture.specialistId, "INTERVIEW_START:" + fixture.participantId,
                "same-key", existing.id, NOW);
        when(fixture.idempotency.findBySpecialistIdAndOperationAndKey(fixture.specialistId,
                "INTERVIEW_START:" + fixture.participantId, "same-key")).thenReturn(Optional.of(replay));
        when(fixture.interviews.findById(existing.id)).thenReturn(Optional.of(existing));
        when(fixture.responses.findByInterviewId(existing.id)).thenReturn(List.of());

        assertThat(fixture.service.start("specialist", fixture.participantId, trainer(), "same-key").id()).isEqualTo(existing.id);
        verify(fixture.interviews, never()).saveAndFlush(any());
    }

    private static ParticipantInterview persistedInterview(Fixture fixture) {
        ParticipantInterview interview = new ParticipantInterview(fixture.participantId, fixture.specialistId, NOW);
        when(fixture.interviews.findByIdAndParticipantIdAndSpecialistId(interview.id, fixture.participantId, fixture.specialistId))
                .thenReturn(Optional.of(interview));
        when(fixture.interviews.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return interview;
    }

    private static List<InterviewResponse> requiredResponses(UUID interviewId) {
        List<InterviewResponse> responses = new ArrayList<>();
        for (ParticipantDocumentationService.Question question : ParticipantDocumentationService.TEMPLATE) {
            if (question.required()) {
                responses.add(new InterviewResponse(interviewId, question, new ParticipantDocumentationService.Answer(question.code(),
                        "answer", question.type() == ParticipantDocumentationService.AnswerType.NUMBER ? BigDecimal.ONE : null,
                        question.type() == ParticipantDocumentationService.AnswerType.DATE ? LocalDate.now() : null,
                        question.type() == ParticipantDocumentationService.AnswerType.MULTI_SELECT ? List.of("yes") : null)));
            }
        }
        return responses;
    }

    private static ActingContext trainer() { return new ActingContext(ProfessionalRole.TRAINER); }
    private static void assertStatus(HttpStatus status, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode()).isEqualTo(status);
    }
    private static Fixture fixture() {
        UUID specialistId = UUID.randomUUID();
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        ParticipantInterviewRepository interviews = org.mockito.Mockito.mock(ParticipantInterviewRepository.class);
        InterviewResponseRepository responses = org.mockito.Mockito.mock(InterviewResponseRepository.class);
        RecordIdempotencyRepository idempotency = org.mockito.Mockito.mock(RecordIdempotencyRepository.class);
        SpecialistRelationshipService relationships = org.mockito.Mockito.mock(SpecialistRelationshipService.class);
        return new Fixture(specialistId, UUID.randomUUID(), interviews, responses, idempotency, relationships,
                new ParticipantDocumentationService(interviews, responses,
                        org.mockito.Mockito.mock(ParticipantNoteRepository.class), org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class),
                        idempotency, accounts, relationships,
                        org.mockito.Mockito.mock(SpecialistAuthorizationPort.class), org.mockito.Mockito.mock(AuditRecorder.class), Clock.fixed(NOW, ZoneOffset.UTC)));
    }
    private record Fixture(UUID specialistId, UUID participantId, ParticipantInterviewRepository interviews,
                           InterviewResponseRepository responses, RecordIdempotencyRepository idempotency,
                           SpecialistRelationshipService relationships, ParticipantDocumentationService service) { }
}
