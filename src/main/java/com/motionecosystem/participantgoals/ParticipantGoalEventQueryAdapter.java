package com.motionecosystem.participantgoals;

import com.motionecosystem.participantgoals.api.ParticipantGoalEventQueryPort;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class ParticipantGoalEventQueryAdapter implements ParticipantGoalEventQueryPort {
    private final ParticipantGoalEventRepository events;
    ParticipantGoalEventQueryAdapter(ParticipantGoalEventRepository events) { this.events = events; }
    @Override public List<ParticipantGoalEventSummary> timeline(UUID participantId, Instant from, Instant to, SeekCursor after, int limit) {
        if (participantId == null || from == null || to == null || limit < 1) return List.of();
        var page = PageRequest.of(0, limit);
        var timeline = after == null
                ? events.timelineWithoutCursor(participantId, from, to, page)
                : events.timelineAfterCursor(participantId, from, to, after.effectiveAt(), after.recordedAt(), after.eventId(), page);
        return timeline.stream().map(this::summary).toList();
    }
    @Override public Optional<ParticipantGoalEventSummary> findByParticipantId(UUID participantId, UUID eventId) {
        return participantId == null || eventId == null ? Optional.empty() : events.findByIdAndParticipantId(eventId, participantId).map(this::summary);
    }
    private ParticipantGoalEventSummary summary(ParticipantGoalEvent e) { return new ParticipantGoalEventSummary(e.id, e.goalId, e.participantId,
            e.observationId, e.eventType.name(), e.fromStatus == null ? null : e.fromStatus.name(), e.toStatus.name(), e.effectiveAt, e.recordedAt,
            e.category, e.title, e.description, e.priority, e.targetDate, e.metricCode, e.observationValue, e.observationUnit, e.measuredAt, e.progressState); }
}
