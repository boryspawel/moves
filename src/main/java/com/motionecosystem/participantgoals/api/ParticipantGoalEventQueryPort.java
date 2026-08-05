package com.motionecosystem.participantgoals.api;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Neutral read boundary for append-only participant-goal mutation snapshots. */
public interface ParticipantGoalEventQueryPort {
    List<ParticipantGoalEventSummary> timeline(UUID participantId, Instant from, Instant to, SeekCursor after, int limit);
    Optional<ParticipantGoalEventSummary> findByParticipantId(UUID participantId, UUID eventId);
    record SeekCursor(Instant effectiveAt, Instant recordedAt, UUID eventId) { }
    record ParticipantGoalEventSummary(UUID eventId, UUID goalId, UUID participantId, UUID observationId, String eventType,
            String fromStatus, String toStatus, Instant effectiveAt, Instant recordedAt, String category, String title,
            String description, int priority, LocalDate targetDate, String metricCode, BigDecimal observationValue,
            String observationUnit, Instant measuredAt, String progressState) { }
}
