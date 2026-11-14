package com.motionecosystem.participantdocumentation.api;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.time.Instant; import java.util.*;
/** Neutral timeline boundary: sensitive record content and answers never cross it. */
public interface ParticipantDocumentationEventQueryPort {
    List<Event> timeline(String subject, UUID participantId, ActingContext actingContext, Instant from, Instant to);
    Optional<Event> find(String subject, UUID participantId, ActingContext actingContext, UUID eventId);
    Optional<DraftInterview> latestDraftInterview(String subject, UUID participantId, ActingContext actingContext);

    List<DocumentationHistoryCandidate> documentationCandidates(String subject, UUID participantId, ActingContext actingContext,
                                                                 Instant fromInclusive, Instant toExclusive);
    List<DocumentationHistoryCandidate> documentationByEventIds(String subject, UUID participantId, ActingContext actingContext,
                                                                  Set<UUID> eventIds);
    Set<UUID> appointmentIdsWithDocumentation(String subject, UUID participantId, ActingContext actingContext,
                                               Set<UUID> appointmentIds);
    List<DocumentationReference> documentationByAppointmentIds(String subject, UUID participantId, ActingContext actingContext,
                                                                 Set<UUID> appointmentIds);

    record Event(UUID eventId, UUID recordId, UUID participantId, String recordType, String action,
                 Instant effectiveAt, Instant recordedAt, String neutralTitle) { }
    record DraftInterview(UUID interviewId, UUID participantId, Instant updatedAt) { }
    record DocumentationHistoryCandidate(UUID eventId, UUID recordId, String recordType, String eventType,
                                         String status, String category, Instant effectiveAt, Instant recordedAt,
                                         UUID appointmentId) { }
    record DocumentationReference(UUID recordId, String category, String status, Instant createdAt,
                                  Instant updatedAt, UUID appointmentId) { }
}
