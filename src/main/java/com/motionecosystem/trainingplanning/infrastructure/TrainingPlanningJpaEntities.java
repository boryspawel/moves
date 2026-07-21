package com.motionecosystem.trainingplanning.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.motionecosystem.trainingplanning.ExercisePrescription;
import com.motionecosystem.trainingplanning.Microcycle;
import com.motionecosystem.trainingplanning.PlannedSession;
import com.motionecosystem.trainingplanning.TrainingCycle;
import com.motionecosystem.trainingplanning.TrainingGoal;
import com.motionecosystem.trainingplanning.TrainingPlan;
import com.motionecosystem.trainingplanning.TrainingPlanningModel;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.Prescription;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity(name = "TrainingGoalJpaEntity")
@Table(name = "training_goal", schema = "training_planning")
class TrainingGoalJpaEntity {
    @Id UUID id;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(nullable = false) String name;
    @Column(name = "created_by_account_id", nullable = false) UUID createdByAccountId;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "revision_id") UUID revisionId;
    @Column String perspective;
    @Column String category;
    @Column String title;
    @Column(length = 1000) String description;
    @Column Integer priority;
    @Column String status;
    @Column(name = "target_date") LocalDate targetDate;

    protected TrainingGoalJpaEntity() {
    }

    TrainingGoalJpaEntity(TrainingGoal source) {
        id = source.id();
        participantAccountId = source.participantAccountId();
        name = source.name();
        createdByAccountId = source.createdByAccountId();
        createdAt = source.createdAt();
    }

    TrainingGoalJpaEntity(TrainingPlanningModel.Goal source) {
        id = source.id();
        participantAccountId = source.participantAccountId();
        name = source.title();
        createdByAccountId = source.createdByAccountId();
        createdAt = source.createdAt();
        revisionId = source.revisionId();
        perspective = source.perspective().name();
        category = source.category();
        title = source.title();
        description = source.description();
        priority = source.priority();
        status = source.status().name();
        targetDate = source.targetDate();
    }
}

@Entity(name = "TrainingPlanJpaEntity")
@Table(name = "training_plan", schema = "training_planning")
class TrainingPlanJpaEntity {
    @Id UUID id;
    @Column(name = "goal_id") UUID goalId;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(name = "created_by_account_id", nullable = false) UUID createdByAccountId;
    @Column(nullable = false) String name;
    @Column(name = "plan_mode", nullable = false) String mode;
    @Column(nullable = false) String status;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(nullable = false, length = 500) String purpose;
    @Column(name = "owner_account_id", nullable = false) UUID ownerAccountId;
    @Column(name = "current_revision_id") UUID currentRevisionId;
    @Version long version;

    protected TrainingPlanJpaEntity() {
    }

    TrainingPlanJpaEntity(TrainingPlan source) {
        id = source.id();
        goalId = source.goalId();
        participantAccountId = source.participantAccountId();
        createdByAccountId = source.createdByAccountId();
        name = source.name();
        mode = source.mode().name();
        status = source.status().name();
        createdAt = source.createdAt();
        purpose = "Legacy V1 adapter";
        ownerAccountId = source.createdByAccountId();
    }

    TrainingPlanJpaEntity(TrainingPlanningModel.PlanDraft source) {
        id = source.id();
        participantAccountId = source.participantAccountId();
        createdByAccountId = source.createdByAccountId();
        name = source.name();
        purpose = source.purpose();
        ownerAccountId = source.ownerAccountId();
        mode = source.mode().name();
        status = source.status().name();
        currentRevisionId = source.currentRevisionId();
        createdAt = source.createdAt();
    }

    void currentRevision(UUID revisionId) { currentRevisionId = revisionId; }
}

@Entity(name = "TrainingCycleJpaEntity")
@Table(name = "training_cycle", schema = "training_planning")
class TrainingCycleJpaEntity {
    @Id UUID id;
    @Column(name = "plan_id", nullable = false) UUID planId;
    @Column(name = "sequence_number", nullable = false) int sequenceNumber;
    @Column(nullable = false) String name;
    @Column(name = "revision_id") UUID revisionId;
    @Column(name = "start_date") LocalDate startDate;
    @Column(name = "end_date") LocalDate endDate;
    @Column(name = "phase_intent", length = 500) String phaseIntent;
    @Column(name = "phase_goal", length = 500) String phaseGoal;

    protected TrainingCycleJpaEntity() {
    }

    TrainingCycleJpaEntity(TrainingCycle source) {
        id = source.id();
        planId = source.planId();
        sequenceNumber = source.sequenceNumber();
        name = source.name();
    }

    TrainingCycleJpaEntity(TrainingPlanningModel.Cycle source) {
        id = source.id();
        planId = source.planId();
        revisionId = source.revisionId();
        sequenceNumber = source.sequenceNumber();
        name = source.name();
        startDate = source.startDate();
        endDate = source.endDate();
        phaseIntent = source.phaseIntent();
        phaseGoal = source.phaseGoal();
    }
}

@Entity(name = "MicrocycleJpaEntity")
@Table(name = "microcycle", schema = "training_planning")
class MicrocycleJpaEntity {
    @Id UUID id;
    @Column(name = "cycle_id", nullable = false) UUID cycleId;
    @Column(name = "sequence_number", nullable = false) int sequenceNumber;
    @Column(nullable = false) String name;
    @Column(name = "start_date") LocalDate startDate;
    @Column(name = "end_date") LocalDate endDate;
    @Column(name = "phase_intent", length = 500) String phaseIntent;
    @Column(name = "phase_goal", length = 500) String phaseGoal;

    protected MicrocycleJpaEntity() {
    }

    MicrocycleJpaEntity(Microcycle source) {
        id = source.id();
        cycleId = source.cycleId();
        sequenceNumber = source.sequenceNumber();
        name = source.name();
    }

    MicrocycleJpaEntity(TrainingPlanningModel.MicrocycleV2 source) {
        id = source.id();
        cycleId = source.cycleId();
        sequenceNumber = source.sequenceNumber();
        name = source.name();
        startDate = source.startDate();
        endDate = source.endDate();
        phaseIntent = source.phaseIntent();
        phaseGoal = source.phaseGoal();
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
    @Column(name = "scheduled_date") LocalDate scheduledDate;
    @Column(name = "available_from") Instant availableFrom;
    @Column(name = "available_to") Instant availableTo;
    @Column(name = "expected_duration_minutes") Integer expectedDurationMinutes;

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

    PlannedSessionJpaEntity(TrainingPlanningModel.Session source) {
        id = source.id();
        microcycleId = source.microcycleId();
        participantAccountId = source.participantAccountId();
        title = source.title();
        kind = PlannedSession.SessionKind.SELF_GUIDED;
        status = PlannedSession.SessionStatus.DRAFT;
        assignedAt = source.createdAt();
        creationSource = "TRAINING_PLANNING";
        scheduledDate = source.scheduledDate();
        availableFrom = source.availableFrom();
        availableTo = source.availableTo();
        expectedDurationMinutes = source.expectedDurationMinutes();
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
    @Column String side;
    @Column(name = "dose_type", nullable = false) String doseType;
    @Column(name = "distance_meters", precision = 10, scale = 2) BigDecimal distanceMeters;
    @Column(name = "target_contacts") Integer contacts;
    @Column(name = "external_load_value", precision = 10, scale = 2) BigDecimal externalLoadValue;
    @Column(name = "external_load_unit") String externalLoadUnit;
    @Column(name = "intensity_type") String intensityType;
    @Column(name = "intensity_value", precision = 8, scale = 2) BigDecimal intensityValue;
    @Column(name = "intensity_zone") String intensityZone;
    @Column String tempo;
    @Column(name = "range_of_motion") String rangeOfMotion;
    @Column(name = "rest_seconds") Integer restSeconds;
    @Column(name = "substitute_group") String substituteGroup;

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
        doseType = "LEGACY_UNTYPED";
    }

    ExercisePrescriptionJpaEntity(Prescription source) {
        id = source.id();
        plannedSessionId = source.plannedSessionId();
        exerciseVersionId = source.exerciseVersionId();
        position = source.position();
        side = source.side().name();
        doseType = source.doseType().name();
        targetSets = source.sets();
        targetRepetitions = source.repetitions();
        targetDurationSeconds = source.durationSeconds();
        distanceMeters = source.distanceMeters();
        contacts = source.contacts();
        externalLoadValue = source.externalLoadValue();
        externalLoadUnit = source.externalLoadUnit();
        intensityType = source.intensityType() == null ? null : source.intensityType().name();
        intensityValue = source.intensityValue();
        intensityZone = source.intensityZone();
        tempo = source.tempo();
        rangeOfMotion = source.rangeOfMotion();
        restSeconds = source.restSeconds();
        substituteGroup = source.substituteGroup();
        notes = source.notes();
    }

    void position(int value) { position = value; }
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
