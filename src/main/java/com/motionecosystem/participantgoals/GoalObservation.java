package com.motionecosystem.participantgoals;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goal_observation", schema = "participant_goals")
class GoalObservation {
    @Id UUID id;
    @Column(name = "goal_id", nullable = false, updatable = false) UUID goalId;
    @Column(name = "outcome_id", nullable = false, updatable = false) UUID outcomeId;
    @Column(name = "participant_id", nullable = false, updatable = false) UUID participantId;
    @Column(nullable = false, precision = 19, scale = 4, updatable = false) BigDecimal value;
    @Column(nullable = false, length = 40, updatable = false) String unit;
    @Column(name = "measurement_method", length = 120, updatable = false) String measurementMethod;
    @Column(name = "measured_at", nullable = false, updatable = false) Instant measuredAt;
    @Column(length = 2000, updatable = false) String note;
    @Column(name = "evidence_source", length = 160, updatable = false) String evidenceSource;
    @Column(name = "recorded_by_account_id", nullable = false, updatable = false) UUID recordedByAccountId;
    @Column(name = "recorded_at", nullable = false, updatable = false) Instant recordedAt;
    protected GoalObservation() { }
    GoalObservation(UUID goalId, UUID outcomeId, UUID participantId, BigDecimal value, String unit, String measurementMethod, Instant measuredAt, String note, String evidenceSource, UUID recordedByAccountId, Instant recordedAt) {
        id = UUID.randomUUID(); this.goalId = goalId; this.outcomeId = outcomeId; this.participantId = participantId; this.value = value; this.unit = unit; this.measurementMethod = measurementMethod; this.measuredAt = measuredAt; this.note = note; this.evidenceSource = evidenceSource; this.recordedByAccountId = recordedByAccountId; this.recordedAt = recordedAt;
    }
}
