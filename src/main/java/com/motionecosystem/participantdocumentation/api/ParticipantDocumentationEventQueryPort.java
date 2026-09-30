package com.motionecosystem.participantdocumentation.api;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.time.Instant; import java.util.*;
/** Neutral timeline boundary: sensitive record content and answers never cross it. */
public interface ParticipantDocumentationEventQueryPort { List<Event> timeline(UUID participantId, Instant from, Instant to); Optional<Event> find(UUID participantId, UUID eventId); Optional<DraftInterview> latestDraftInterview(String subject, UUID participantId, ActingContext actingContext); record Event(UUID eventId,UUID recordId,UUID participantId,String recordType,String action,Instant effectiveAt,Instant recordedAt,String neutralTitle){} record DraftInterview(UUID interviewId, UUID participantId, Instant updatedAt){} }
