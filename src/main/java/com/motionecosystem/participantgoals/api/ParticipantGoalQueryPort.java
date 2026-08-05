package com.motionecosystem.participantgoals.api;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Neutral read boundary for active participant goals; it deliberately exposes no persistence entities. */
public interface ParticipantGoalQueryPort {
    List<ParticipantGoalSummary> findActiveByParticipantId(UUID participantId);
    Optional<ParticipantGoalSummary> findById(UUID goalId);
    /** Neutral observation query boundary; callers receive projections rather than persistence entities. */
    default ObservationHistory findObservationHistory(UUID goalId, UUID outcomeId, Instant measuredBefore, Instant recordedBefore, UUID idBefore, int limit) { return new ObservationHistory(List.of(), null); }
    record ParticipantGoalSummary(UUID id, UUID participantId, String category, String title, String description, int priority, LocalDate targetDate, String status, List<OutcomeSnapshot> outcomes, long version, Instant createdAt, Instant updatedAt) { }
    record OutcomeSnapshot(String metricCode, BigDecimal baseline, BigDecimal targetValue, String unit, int position) { }
    record ObservationSnapshot(UUID id, UUID goalId, UUID outcomeId, UUID participantId, BigDecimal value, String unit, String measurementMethod, Instant measuredAt, Instant recordedAt) { }
    record ObservationHistory(List<ObservationSnapshot> items, String nextCursor) { }
}
