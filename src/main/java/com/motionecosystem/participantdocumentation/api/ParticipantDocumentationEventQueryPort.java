package com.motionecosystem.participantdocumentation.api;
import java.time.Instant; import java.util.*;
/** Neutral timeline boundary: sensitive record content and answers never cross it. */
public interface ParticipantDocumentationEventQueryPort { List<Event> timeline(UUID participantId, Instant from, Instant to); Optional<Event> find(UUID participantId, UUID eventId); record Event(UUID eventId,UUID recordId,UUID participantId,String recordType,String action,Instant effectiveAt,Instant recordedAt,String neutralTitle){} }
