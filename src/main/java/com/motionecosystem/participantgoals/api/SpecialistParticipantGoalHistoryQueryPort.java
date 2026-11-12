package com.motionecosystem.participantgoals.api;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owner-authorized neutral goal facts for specialist encounter-history composition. */
public interface SpecialistParticipantGoalHistoryQueryPort {
    List<GoalHistoryCandidate> goalHistoryCandidates(String subject, UUID participantId, ActingContext context,
                                                     Instant fromInclusive, Instant toExclusive);
    List<GoalHistoryCandidate> goalHistoryByEventIds(String subject, UUID participantId, ActingContext context,
                                                     java.util.Set<UUID> eventIds);

    record GoalHistoryCandidate(UUID eventId, UUID goalId, UUID outcomeId, String eventType, String status,
                                Instant effectiveAt, Instant recordedAt, UUID sourceMeasurementId) { }
}
