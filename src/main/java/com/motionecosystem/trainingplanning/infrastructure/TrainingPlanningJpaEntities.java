package com.motionecosystem.trainingplanning.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motionecosystem.trainingplanning.ExercisePrescription;
import com.motionecosystem.trainingplanning.Microcycle;
import com.motionecosystem.trainingplanning.PlannedSession;
import com.motionecosystem.trainingplanning.TrainingCycle;
import com.motionecosystem.trainingplanning.TrainingGoal;
import com.motionecosystem.trainingplanning.TrainingPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "TrainingGoalJpaEntity")
@Table(name = "training_goal", schema = "training_planning")
class TrainingGoalJpaEntity {
    @Id UUID id;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(nullable = false) String name;
    @Column(name = "created_by_account_id", nullable = false) UUID createdByAccountId;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

    protected TrainingGoalJpaEntity() {
    }

    TrainingGoalJpaEntity(TrainingGoal source) {
        id = source.id();
        participantAccountId = source.participantAccountId();
        name = source.name();
        createdByAccountId = source.createdByAccountId();
        createdAt = source.createdAt();
    }
}

@Entity(name = "TrainingPlanJpaEntity")
@Table(name = "training_plan", schema = "training_planning")
class TrainingPlanJpaEntity {
    @Id UUID id;
    @Column(name = "goal_id", nullable = false) UUID goalId;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(name = "created_by_account_id", nullable = false) UUID createdByAccountId;
    @Column(nullable = false) String name;
    @Enumerated(EnumType.STRING) @Column(name = "plan_mode", nullable = false) TrainingPlan.PlanMode mode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) TrainingPlan.PlanStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

    protected TrainingPlanJpaEntity() {
    }

    TrainingPlanJpaEntity(TrainingPlan source) {
        id = source.id();
        goalId = source.goalId();
        participantAccountId = source.participantAccountId();
        createdByAccountId = source.createdByAccountId();
        name = source.name();
        mode = source.mode();
        status = source.status();
        createdAt = source.createdAt();
    }
}

@Entity(name = "TrainingCycleJpaEntity")
@Table(name = "training_cycle", schema = "training_planning")
class TrainingCycleJpaEntity {
    @Id UUID id;
    @Column(name = "plan_id", nullable = false) UUID planId;
    @Column(name = "sequence_number", nullable = false) int sequenceNumber;
    @Column(nullable = false) String name;

    protected TrainingCycleJpaEntity() {
    }

    TrainingCycleJpaEntity(TrainingCycle source) {
        id = source.id();
        planId = source.planId();
        sequenceNumber = source.sequenceNumber();
        name = source.name();
    }
}

@Entity(name = "MicrocycleJpaEntity")
@Table(name = "microcycle", schema = "training_planning")
class MicrocycleJpaEntity {
    @Id UUID id;
    @Column(name = "cycle_id", nullable = false) UUID cycleId;
    @Column(name = "sequence_number", nullable = false) int sequenceNumber;
    @Column(nullable = false) String name;

    protected MicrocycleJpaEntity() {
    }

    MicrocycleJpaEntity(Microcycle source) {
        id = source.id();
        cycleId = source.cycleId();
        sequenceNumber = source.sequenceNumber();
        name = source.name();
    }
}

@Entity(name = "PlannedSessionJpaEntity")
@Table(name = "planned_session", schema = "training_planning")
class PlannedSessionJpaEntity {
    @Id UUID id;
    @Column(name = "microcycle_id", nullable = false) UUID microcycleId;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(nullable = false) String title;
    @Enumerated(EnumType.STRING) @Column(name = "session_kind", nullable = false) PlannedSession.SessionKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false) PlannedSession.SessionStatus status;
    @Column(name = "assigned_at", nullable = false) Instant assignedAt;
    @Column(name = "creation_source", nullable = false) String creationSource;

    protected PlannedSessionJpaEntity() {
    }

    PlannedSessionJpaEntity(PlannedSession source) {
        id = source.id();
        microcycleId = source.microcycleId();
        participantAccountId = source.participantAccountId();
        title = source.title();
        kind = source.kind();
        status = source.status();
        assignedAt = source.assignedAt();
        creationSource = "TRAINING_PLANNING";
    }

    void complete() {
        if (status != PlannedSession.SessionStatus.ASSIGNED) {
            throw new IllegalStateException("only an assigned session can be completed");
        }
        status = PlannedSession.SessionStatus.COMPLETED;
    }

    UUID id() { return id; }
    UUID participantAccountId() { return participantAccountId; }
    String title() { return title; }
    PlannedSession.SessionKind kind() { return kind; }
    PlannedSession.SessionStatus status() { return status; }
    Instant assignedAt() { return assignedAt; }
}

@Entity(name = "ExercisePrescriptionJpaEntity")
@Table(name = "exercise_prescription", schema = "training_planning")
class ExercisePrescriptionJpaEntity {
    @Id UUID id;
    @Column(name = "planned_session_id", nullable = false) UUID plannedSessionId;
    @Column(name = "exercise_version_id", nullable = false) UUID exerciseVersionId;
    @Column(nullable = false) int position;
    @Column(name = "target_sets") Integer targetSets;
    @Column(name = "target_repetitions") Integer targetRepetitions;
    @Column(name = "target_duration_seconds") Integer targetDurationSeconds;
    @Column(name = "target_load_kg", precision = 8, scale = 2) BigDecimal targetLoadKg;
    @Column(length = 500) String notes;

    protected ExercisePrescriptionJpaEntity() {
    }

    ExercisePrescriptionJpaEntity(ExercisePrescription source) {
        id = source.id();
        plannedSessionId = source.plannedSessionId();
        exerciseVersionId = source.exerciseVersionId();
        position = source.position();
        targetSets = source.targetSets();
        targetRepetitions = source.targetRepetitions();
        targetDurationSeconds = source.targetDurationSeconds();
        targetLoadKg = source.targetLoadKg();
        notes = source.notes();
    }

    UUID id() { return id; }
    UUID plannedSessionId() { return plannedSessionId; }
    UUID exerciseVersionId() { return exerciseVersionId; }
    int position() { return position; }
    Integer targetSets() { return targetSets; }
    Integer targetRepetitions() { return targetRepetitions; }
    Integer targetDurationSeconds() { return targetDurationSeconds; }
    BigDecimal targetLoadKg() { return targetLoadKg; }
    String notes() { return notes; }
}
