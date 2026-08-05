package com.motionecosystem.participantgoals;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goal_outcome", schema = "participant_goals", uniqueConstraints = {@UniqueConstraint(columnNames = {"goal_id", "metric_code"}), @UniqueConstraint(columnNames = {"goal_id", "position"})})
class GoalOutcome {
    @Id UUID id;
    @Column(name = "goal_id", nullable = false) UUID goalId;
    @Column(name = "metric_code", nullable = false, length = 80) String metricCode;
    @Column(precision = 19, scale = 4) BigDecimal baseline;
    @Column(name = "target_value", nullable = false, precision = 19, scale = 4) BigDecimal targetValue;
    @Column(nullable = false, length = 40) String unit;
    @Column(name = "measurement_method", length = 120) String measurementMethod;
    @Enumerated(EnumType.STRING) @Column(name = "target_comparator", length = 16) TargetComparator targetComparator;
    @Column(nullable = false) int position;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected GoalOutcome() { }
    GoalOutcome(UUID goalId, String metricCode, BigDecimal baseline, BigDecimal targetValue, String unit, String measurementMethod, TargetComparator targetComparator, int position, Instant now) { id = UUID.randomUUID(); this.goalId = goalId; this.metricCode = metricCode; this.baseline = baseline; this.targetValue = targetValue; this.unit = unit; this.measurementMethod = measurementMethod; this.targetComparator = targetComparator; this.position = position; createdAt = now; }
    GoalOutcome(UUID goalId, String metricCode, BigDecimal targetValue, String unit, String measurementMethod, TargetComparator targetComparator, int position, Instant now) { this(goalId, metricCode, null, targetValue, unit, measurementMethod, targetComparator, position, now); }
}
