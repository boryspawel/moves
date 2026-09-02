package com.motionecosystem.participantdocumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantDocumentationTimelineTest {
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
                org.mockito.Mockito.mock(SpecialistRelationshipService.class), org.mockito.Mockito.mock(SpecialistAuthorizationPort.class),
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
