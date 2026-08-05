package com.motionecosystem.participantgoals;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Append-only snapshot of a participant-goal mutation. */
@Entity
@Table(name = "participant_goal_event", schema = "participant_goals")
class ParticipantGoalEvent {
    enum Type { BASELINE, CREATED, UPDATED, OBSERVATION_RECORDED, ACHIEVED, CANCELLED }
    @Id UUID id;
    @Column(name = "goal_id", nullable = false, updatable = false) UUID goalId;
    @Column(name = "participant_id", nullable = false, updatable = false) UUID participantId;
    @Column(name = "observation_id", updatable = false) UUID observationId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, updatable = false) Type eventType;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", updatable = false) ParticipantGoal.Status fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, updatable = false) ParticipantGoal.Status toStatus;
    @Column(name = "effective_at", nullable = false, updatable = false) Instant effectiveAt;
    @Column(name = "recorded_at", nullable = false, updatable = false) Instant recordedAt;
    @Column(name = "category", nullable = false, length = 32, updatable = false) String category;
    @Column(name = "title", nullable = false, length = 160, updatable = false) String title;
    @Column(name = "description", length = 2000, updatable = false) String description;
    @Column(name = "priority", nullable = false, updatable = false) int priority;
    @Column(name = "target_date", updatable = false) LocalDate targetDate;
    @Column(name = "metric_code", length = 80, updatable = false) String metricCode;
    @Column(name = "observation_value", precision = 19, scale = 4, updatable = false) BigDecimal observationValue;
    @Column(name = "observation_unit", length = 40, updatable = false) String observationUnit;
    @Column(name = "measured_at", updatable = false) Instant measuredAt;
    @Column(name = "progress_state", length = 32, updatable = false) String progressState;

    protected ParticipantGoalEvent() { }

    ParticipantGoalEvent(Type type, ParticipantGoal goal, ParticipantGoal.Status fromStatus, Instant effectiveAt, Instant recordedAt,
                         GoalObservation observation, GoalOutcome outcome, String progressState) {
        id = UUID.randomUUID(); goalId = goal.id; participantId = goal.participantId; observationId = observation == null ? null : observation.id;
        eventType = type; this.fromStatus = fromStatus; toStatus = goal.status; this.effectiveAt = effectiveAt; this.recordedAt = recordedAt;
        category = goal.category.name(); title = goal.title; description = goal.description; priority = goal.priority; targetDate = goal.targetDate;
        metricCode = outcome == null ? null : outcome.metricCode; observationValue = observation == null ? null : observation.value;
        observationUnit = observation == null ? null : observation.unit; measuredAt = observation == null ? null : observation.measuredAt;
        this.progressState = progressState;
    }
}
