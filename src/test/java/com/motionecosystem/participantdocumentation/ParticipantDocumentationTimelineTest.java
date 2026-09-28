package com.motionecosystem.participantdocumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantDocumentationTimelineTest {
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
        UUID interviewId = UUID.randomUUID();
        ParticipantDocumentationEvent interviewEvent = new ParticipantDocumentationEvent(interviewId, participantId, "INTERVIEW", "COMPLETED",
                Instant.parse("2030-06-10T12:00:00Z"), "Interview completed");
        ParticipantDocumentationEventRepository events = org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class);
        when(events.findByParticipantIdAndEffectiveAtGreaterThanEqualAndEffectiveAtLessThanOrderByEffectiveAtDesc(participantId,
                Instant.parse("2030-06-10T00:00:00Z"), Instant.parse("2030-06-11T00:00:00Z"))).thenReturn(List.of(interviewEvent));
        when(events.findByIdAndParticipantId(interviewEvent.id, participantId)).thenReturn(Optional.of(interviewEvent));
        ParticipantDocumentationService service = service(events);

        var timeline = service.timeline(participantId, Instant.parse("2030-06-10T00:00:00Z"), Instant.parse("2030-06-11T00:00:00Z"));
        var detail = service.find(participantId, interviewEvent.id);

        assertThat(timeline).singleElement().satisfies(event -> assertNeutralMetadata(event, interviewEvent, interviewId, participantId));
        assertThat(detail).hasValueSatisfying(event -> assertNeutralMetadata(event, interviewEvent, interviewId, participantId));
    }

    private static ParticipantDocumentationService service(ParticipantDocumentationEventRepository events) {
        return new ParticipantDocumentationService(org.mockito.Mockito.mock(ParticipantInterviewRepository.class),
                org.mockito.Mockito.mock(InterviewResponseRepository.class), org.mockito.Mockito.mock(ParticipantNoteRepository.class), events,
                org.mockito.Mockito.mock(RecordIdempotencyRepository.class), org.mockito.Mockito.mock(CurrentAccountService.class),
                org.mockito.Mockito.mock(SpecialistAuthorizationPort.class),
                org.mockito.Mockito.mock(AuditRecorder.class), Clock.fixed(Instant.parse("2030-06-10T12:00:00Z"), ZoneOffset.UTC));
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
