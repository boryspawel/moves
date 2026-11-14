package com.motionecosystem.participantdocumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.calendar.api.SpecialistAppointmentHistoryQueryPort;
import com.motionecosystem.participantdocumentation.api.ParticipantDocumentationEventQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantDocumentationTimelineTest {
    @Test
    void timelineOnlyShowsOwnDocumentationAfterScopedAuthorization() {
        UUID owner = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        Instant at = Instant.parse("2030-06-10T12:00:00Z");
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        ParticipantNoteRepository notes = org.mockito.Mockito.mock(ParticipantNoteRepository.class);
        ParticipantDocumentationEventRepository events = org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class);
        SpecialistAuthorizationPort authorization = org.mockito.Mockito.mock(SpecialistAuthorizationPort.class);
        ParticipantNote own = new ParticipantNote(participantId, owner, "GENERAL", "mine", "private", null, at);
        ParticipantNote foreign = new ParticipantNote(participantId, otherOwner, "GENERAL", "other", "private", null, at);
        ParticipantDocumentationEvent ownEvent = new ParticipantDocumentationEvent(own.id, participantId, "NOTE", "CREATED", at, "Note created");
        ParticipantDocumentationEvent foreignEvent = new ParticipantDocumentationEvent(foreign.id, participantId, "NOTE", "CREATED", at, "Note created");
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(owner, "specialist", ProfileType.SPECIALIST));
        when(events.findByParticipantIdAndEffectiveAtGreaterThanEqualAndEffectiveAtLessThanOrderByEffectiveAtDesc(
                participantId, at.minusSeconds(1), at.plusSeconds(1))).thenReturn(List.of(ownEvent, foreignEvent));
        when(events.findByIdAndParticipantId(foreignEvent.id, participantId)).thenReturn(Optional.of(foreignEvent));
        when(notes.findByIdInAndParticipantIdAndSpecialistId(any(), org.mockito.ArgumentMatchers.eq(participantId),
                org.mockito.ArgumentMatchers.eq(owner))).thenReturn(List.of(own));
        ParticipantDocumentationService service = historyService(accounts, notes, events, authorization,
                org.mockito.Mockito.mock(SpecialistAppointmentHistoryQueryPort.class));
        ActingContext context = new ActingContext(ProfessionalRole.TRAINER);

        assertThat(service.timeline("specialist", participantId, context, at.minusSeconds(1), at.plusSeconds(1)))
                .extracting(ParticipantDocumentationEventQueryPort.Event::eventId).containsExactly(ownEvent.id);
        assertThat(service.find("specialist", participantId, context, foreignEvent.id)).isEmpty();
        org.mockito.Mockito.verify(authorization, org.mockito.Mockito.atLeastOnce()).requireCapabilities(
                org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.eq(participantId),
                org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.eq(Set.of(SpecialistAuthorizationPort.Capability.MANAGE_PARTICIPANT_RECORDS,
                        SpecialistAuthorizationPort.Capability.MANAGE_PARTICIPANT_DOCUMENTATION)),
                org.mockito.ArgumentMatchers.eq(SpecialistAuthorizationPort.Purpose.PERFORMANCE_PLANNING));
    }

    @Test
    void documentationCandidatesRetainsAnUnlinkedNoteWhenValidAppointmentsIsEmptyImmutableSet() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Instant at = Instant.parse("2030-06-10T12:00:00Z");
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        ParticipantNoteRepository notes = org.mockito.Mockito.mock(ParticipantNoteRepository.class);
        ParticipantDocumentationEventRepository events = org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class);
        ParticipantNote note = new ParticipantNote(participantId, specialistId, "GENERAL", "title", "body", null, at);
        ParticipantDocumentationEvent event = new ParticipantDocumentationEvent(note.id, participantId, "NOTE", "CREATED", at, "Note created");
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(events.findByParticipantIdAndEffectiveAtGreaterThanEqualAndEffectiveAtLessThanOrderByEffectiveAtDesc(participantId, at.minusSeconds(1), at.plusSeconds(1))).thenReturn(List.of(event));
        when(notes.findByIdInAndParticipantIdAndSpecialistId(any(), org.mockito.ArgumentMatchers.eq(participantId), org.mockito.ArgumentMatchers.eq(specialistId))).thenReturn(List.of(note));
        ParticipantDocumentationService service = historyService(accounts, notes, events, org.mockito.Mockito.mock(SpecialistAuthorizationPort.class),
                org.mockito.Mockito.mock(SpecialistAppointmentHistoryQueryPort.class));

        var candidate = service.documentationCandidates("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER), at.minusSeconds(1), at.plusSeconds(1));

        assertThat(candidate).singleElement().extracting(item -> item.eventId(), item -> item.recordId(), item -> item.appointmentId())
                .containsExactly(event.id, note.id, null);
    }

    @Test
    void documentationByEventIdsRetainsAnUnlinkedNoteWhenValidAppointmentsIsEmptyImmutableSet() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Instant at = Instant.parse("2030-06-10T12:00:00Z");
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        ParticipantNoteRepository notes = org.mockito.Mockito.mock(ParticipantNoteRepository.class);
        ParticipantDocumentationEventRepository events = org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class);
        ParticipantNote note = new ParticipantNote(participantId, specialistId, "GENERAL", "title", "body", null, at);
        ParticipantDocumentationEvent event = new ParticipantDocumentationEvent(note.id, participantId, "NOTE", "CREATED", at, "Note created");
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(events.findByIdInAndParticipantId(Set.of(event.id), participantId)).thenReturn(List.of(event));
        when(notes.findByIdInAndParticipantIdAndSpecialistId(any(), org.mockito.ArgumentMatchers.eq(participantId), org.mockito.ArgumentMatchers.eq(specialistId))).thenReturn(List.of(note));
        ParticipantDocumentationService service = historyService(accounts, notes, events, org.mockito.Mockito.mock(SpecialistAuthorizationPort.class),
                org.mockito.Mockito.mock(SpecialistAppointmentHistoryQueryPort.class));

        var candidate = service.documentationByEventIds("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER), Set.of(event.id));

        assertThat(candidate).singleElement().extracting(item -> item.eventId(), item -> item.recordId(), item -> item.appointmentId())
                .containsExactly(event.id, note.id, null);
    }

    @Test
    void historyCandidateSanitizesAnUnverifiedNoteAppointmentAndDoesNotExposeContent() {
        UUID specialistId = UUID.randomUUID(); UUID participantId = UUID.randomUUID(); UUID appointmentId = UUID.randomUUID();
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        ParticipantNoteRepository notes = org.mockito.Mockito.mock(ParticipantNoteRepository.class);
        ParticipantDocumentationEventRepository events = org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class);
        SpecialistAppointmentHistoryQueryPort appointments = org.mockito.Mockito.mock(SpecialistAppointmentHistoryQueryPort.class);
        SpecialistAuthorizationPort authorization = org.mockito.Mockito.mock(SpecialistAuthorizationPort.class);
        Instant at = Instant.parse("2030-06-10T12:00:00Z");
        ParticipantNote note = new ParticipantNote(participantId, specialistId, "APPOINTMENT_SUMMARY", "private title", "private body", appointmentId, at);
        ParticipantDocumentationEvent event = new ParticipantDocumentationEvent(note.id, participantId, "NOTE", "CREATED", at, "Note created");
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        when(events.findByParticipantIdAndEffectiveAtGreaterThanEqualAndEffectiveAtLessThanOrderByEffectiveAtDesc(participantId, at.minusSeconds(1), at.plusSeconds(1))).thenReturn(List.of(event));
        when(notes.findByIdInAndParticipantIdAndSpecialistId(any(), org.mockito.ArgumentMatchers.eq(participantId), org.mockito.ArgumentMatchers.eq(specialistId))).thenReturn(List.of(note));
        when(appointments.appointmentsByIds("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER), Set.of(appointmentId))).thenReturn(List.of());
        ParticipantDocumentationService service = historyService(accounts, notes, events, authorization, appointments);

        var candidate = service.documentationCandidates("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER), at.minusSeconds(1), at.plusSeconds(1));

        assertThat(candidate).singleElement().extracting(item -> item.recordId(), item -> item.category(), item -> item.status(), item -> item.appointmentId())
                .containsExactly(note.id, "APPOINTMENT_SUMMARY", "DRAFT", null);
    }

    @Test
    void historyCandidateOmitsFactsOnlyForForbiddenAuthorizationAndPropagatesOtherFailures() {
        UUID participantId = UUID.randomUUID(); UUID specialistId = UUID.randomUUID();
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        SpecialistAuthorizationPort authorization = org.mockito.Mockito.mock(SpecialistAuthorizationPort.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(authorization).requireCapabilities(any(), any(), any(), any(), any());
        ParticipantDocumentationService service = historyService(accounts, org.mockito.Mockito.mock(ParticipantNoteRepository.class),
                org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class), authorization, org.mockito.Mockito.mock(SpecialistAppointmentHistoryQueryPort.class));

        assertThat(service.documentationCandidates("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER), Instant.EPOCH, Instant.EPOCH.plusSeconds(1))).isEmpty();
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST))
                .when(authorization).requireCapabilities(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.documentationCandidates("specialist", participantId, new ActingContext(ProfessionalRole.TRAINER), Instant.EPOCH, Instant.EPOCH.plusSeconds(1)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }
    @Test
    void returnsAppointmentSummaryOnlyAfterTheNormalSpecialistActingContextAuthorization() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        ParticipantNoteRepository notes = org.mockito.Mockito.mock(ParticipantNoteRepository.class);
        SpecialistAuthorizationPort authorization = org.mockito.Mockito.mock(SpecialistAuthorizationPort.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        ParticipantNote note = new ParticipantNote(participantId, specialistId, "APPOINTMENT_SUMMARY", "Podsumowanie spotkania",
                "Ustalono dalszy plan", appointmentId, Instant.parse("2030-06-10T12:00:00Z"));
        when(notes.findTopByParticipantIdAndSpecialistIdAndAppointmentIdAndCategoryOrderByCreatedAtDesc(
                participantId, specialistId, appointmentId, "APPOINTMENT_SUMMARY")).thenReturn(Optional.of(note));
        ParticipantDocumentationService service = new ParticipantDocumentationService(
                org.mockito.Mockito.mock(ParticipantInterviewRepository.class), org.mockito.Mockito.mock(InterviewResponseRepository.class), notes,
                org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class), org.mockito.Mockito.mock(RecordIdempotencyRepository.class), accounts,
                authorization, org.mockito.Mockito.mock(AuditRecorder.class), Clock.fixed(Instant.parse("2030-06-10T12:00:00Z"), ZoneOffset.UTC));

        var summary = service.findAppointmentSummary("specialist", participantId, appointmentId, new ActingContext(ProfessionalRole.TRAINER));

        assertThat(summary).hasValueSatisfying(item -> {
            assertThat(item.title()).isEqualTo("Podsumowanie spotkania");
            assertThat(item.content()).isEqualTo("Ustalono dalszy plan");
        });
        org.mockito.Mockito.verify(authorization).requireCapabilities(org.mockito.ArgumentMatchers.eq(specialistId),
                org.mockito.ArgumentMatchers.eq(participantId), org.mockito.ArgumentMatchers.eq(new ActingContext(ProfessionalRole.TRAINER)),
                org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exposesOnlyNeutralTimelineMetadataAndKeepsEventAndRecordIdentifiersDistinct() {
        UUID participantId = UUID.randomUUID();
        UUID specialistId = UUID.randomUUID();
        ParticipantInterview interview = new ParticipantInterview(participantId, specialistId, Instant.parse("2030-06-10T12:00:00Z"));
        UUID interviewId = interview.id;
        ParticipantDocumentationEvent interviewEvent = new ParticipantDocumentationEvent(interviewId, participantId, "INTERVIEW", "COMPLETED",
                Instant.parse("2030-06-10T12:00:00Z"), "Interview completed");
        ParticipantDocumentationEventRepository events = org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class);
        when(events.findByParticipantIdAndEffectiveAtGreaterThanEqualAndEffectiveAtLessThanOrderByEffectiveAtDesc(participantId,
                Instant.parse("2030-06-10T00:00:00Z"), Instant.parse("2030-06-11T00:00:00Z"))).thenReturn(List.of(interviewEvent));
        when(events.findByIdAndParticipantId(interviewEvent.id, participantId)).thenReturn(Optional.of(interviewEvent));
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        ParticipantInterviewRepository interviews = org.mockito.Mockito.mock(ParticipantInterviewRepository.class);
        when(interviews.findByIdInAndParticipantIdAndSpecialistId(any(),
                org.mockito.ArgumentMatchers.eq(participantId), org.mockito.ArgumentMatchers.eq(specialistId)))
                .thenReturn(List.of(interview));
        ParticipantDocumentationService service = new ParticipantDocumentationService(interviews,
                org.mockito.Mockito.mock(InterviewResponseRepository.class), org.mockito.Mockito.mock(ParticipantNoteRepository.class), events,
                org.mockito.Mockito.mock(RecordIdempotencyRepository.class), accounts,
                org.mockito.Mockito.mock(SpecialistAuthorizationPort.class), org.mockito.Mockito.mock(AuditRecorder.class),
                Clock.fixed(Instant.parse("2030-06-10T12:00:00Z"), ZoneOffset.UTC));

        ActingContext context = new ActingContext(ProfessionalRole.TRAINER);
        var timeline = service.timeline("specialist", participantId, context,
                Instant.parse("2030-06-10T00:00:00Z"), Instant.parse("2030-06-11T00:00:00Z"));
        var detail = service.find("specialist", participantId, context, interviewEvent.id);

        assertThat(timeline).singleElement().satisfies(event -> assertNeutralMetadata(event, interviewEvent, interviewId, participantId));
        assertThat(detail).hasValueSatisfying(event -> assertNeutralMetadata(event, interviewEvent, interviewId, participantId));
    }

    private static ParticipantDocumentationService historyService(CurrentAccountService accounts, ParticipantNoteRepository notes,
                                                                   ParticipantDocumentationEventRepository events, SpecialistAuthorizationPort authorization,
                                                                   SpecialistAppointmentHistoryQueryPort appointments) {
        return new ParticipantDocumentationService(org.mockito.Mockito.mock(ParticipantInterviewRepository.class),
                org.mockito.Mockito.mock(InterviewResponseRepository.class), notes, events, org.mockito.Mockito.mock(RecordIdempotencyRepository.class),
                accounts, authorization, appointments, org.mockito.Mockito.mock(AuditRecorder.class), Clock.systemUTC());
    }

    private static void assertNeutralMetadata(com.motionecosystem.participantdocumentation.api.ParticipantDocumentationEventQueryPort.Event event,
                                              ParticipantDocumentationEvent persisted, UUID recordId, UUID participantId) {
        assertThat(event.eventId()).isEqualTo(persisted.id);
        assertThat(event.recordId()).isEqualTo(recordId);
        assertThat(event.participantId()).isEqualTo(participantId);
        assertThat(event.recordType()).isEqualTo("INTERVIEW");
        assertThat(event.action()).isEqualTo("COMPLETED");
        assertThat(event.neutralTitle()).isEqualTo("Interview completed");
    }
}
