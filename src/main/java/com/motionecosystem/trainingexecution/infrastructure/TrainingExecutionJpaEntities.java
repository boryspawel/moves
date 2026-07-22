package com.motionecosystem.trainingexecution.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motionecosystem.trainingexecution.SessionExecutionPersistence.AlertData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.CorrectionData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ExecutionData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ReportData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ResultData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ExecutedObservationData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.Post24hData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "SessionExecutionJpaEntity")
@Table(name = "session_execution", schema = "training_execution")
class SessionExecutionJpaEntity {
    @Id UUID id;
    @Column(name = "planned_session_id", nullable = false) UUID plannedSessionId;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(name = "declared_completion", nullable = false) boolean declaredCompletion;
    @Column(name = "idempotency_key", nullable = false, length = 120) String idempotencyKey;
    @Column(name = "recorded_at", nullable = false, updatable = false) Instant recordedAt;
    @Column(name = "declaration_event_id") UUID declarationEventId;
    @Column(name = "projection_status") String projectionStatus;

    protected SessionExecutionJpaEntity() {
    }

    SessionExecutionJpaEntity(ExecutionData source) {
        id = source.id();
        plannedSessionId = source.plannedSessionId();
        participantAccountId = source.participantAccountId();
        declaredCompletion = source.declaredCompletion();
        idempotencyKey = source.idempotencyKey();
        recordedAt = source.recordedAt();
        declarationEventId = source.declarationEventId();
        projectionStatus = source.projectionStatus();
    }

    UUID id() { return id; }
    UUID participantAccountId() { return participantAccountId; }

    ExecutionData data() {
        return new ExecutionData(id, plannedSessionId, participantAccountId,
                declaredCompletion, idempotencyKey, recordedAt, declarationEventId, projectionStatus);
    }
}

@Entity(name = "ExerciseResultJpaEntity")
@Table(name = "exercise_result", schema = "training_execution")
class ExerciseResultJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id", nullable = false) UUID sessionExecutionId;
    @Column(name = "exercise_prescription_id", nullable = false) UUID exercisePrescriptionId;
    @Column(name = "exercise_version_id") UUID exerciseVersionId;
    @Column(name = "actual_sets") Integer actualSets;
    @Column(name = "actual_repetitions") Integer actualRepetitions;
    @Column(name = "actual_duration_seconds") Integer actualDurationSeconds;
    @Column(name = "actual_load_kg", precision = 8, scale = 2) BigDecimal actualLoadKg;
    @Column(name = "actual_contacts") Integer actualContacts;
    @Column(name = "actual_distance_meters") BigDecimal actualDistanceMeters;
    @Column(name = "actual_external_load_value") BigDecimal actualExternalLoadValue;
    @Column(name = "actual_external_load_unit") String actualExternalLoadUnit;
    @Column(name = "actual_intensity_type") String actualIntensityType;
    @Column(name = "actual_intensity_value") BigDecimal actualIntensityValue;
    @Column(name = "actual_intensity_zone") String actualIntensityZone;
    String side;
    boolean modified;
    boolean skipped;
    @Column(name = "observation_mode") String observationMode;

    protected ExerciseResultJpaEntity() {
    }

    ExerciseResultJpaEntity(ResultData source) {
        id = source.id();
        sessionExecutionId = source.sessionExecutionId();
        exercisePrescriptionId = source.exercisePrescriptionId();
        exerciseVersionId = source.exerciseVersionId();
        actualSets = source.actualSets();
        actualRepetitions = source.actualRepetitions();
        actualDurationSeconds = source.actualDurationSeconds();
        actualContacts = source.actualContacts();
        actualDistanceMeters = source.actualDistanceMeters();
        actualLoadKg = source.actualLoadKg();
        actualExternalLoadValue = source.actualExternalLoadValue();
        actualExternalLoadUnit = source.actualExternalLoadUnit();
        actualIntensityType = source.actualIntensityType();
        actualIntensityValue = source.actualIntensityValue();
        actualIntensityZone = source.actualIntensityZone();
        side = source.side();
        modified = source.modified();
        skipped = source.skipped();
        observationMode = source.observationMode();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    ResultData data() {
        return new ResultData(id, sessionExecutionId, exercisePrescriptionId, exerciseVersionId,
                actualSets, actualRepetitions, actualDurationSeconds, actualContacts,
                actualDistanceMeters, actualLoadKg, actualExternalLoadValue, actualExternalLoadUnit,
                actualIntensityType, actualIntensityValue, actualIntensityZone, side,
                modified, skipped, observationMode);
    }
}

@Entity(name = "PainDifficultyReportJpaEntity")
@Table(name = "pain_difficulty_report", schema = "training_execution")
class PainDifficultyReportJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id", nullable = false, unique = true) UUID sessionExecutionId;
    @Column(name = "pain_level", nullable = false) int painLevel;
    @Column(name = "difficulty_level", nullable = false) int difficultyLevel;
    @Column(name = "technique_confidence_level") Integer techniqueConfidenceLevel;
    @Column(length = 500) String note;
    @Column(name = "reported_at", nullable = false, updatable = false) Instant reportedAt;
    @Column(name = "session_rpe") Integer sessionRpe;
    @Column(name = "observation_mode") String observationMode;

    protected PainDifficultyReportJpaEntity() {
    }

    PainDifficultyReportJpaEntity(ReportData source) {
        id = source.id();
        sessionExecutionId = source.sessionExecutionId();
        painLevel = source.painLevel();
        difficultyLevel = source.difficultyLevel();
        techniqueConfidenceLevel = source.techniqueConfidenceLevel();
        note = source.note();
        sessionRpe = source.sessionRpe();
        observationMode = source.observationMode();
        reportedAt = source.reportedAt();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    ReportData data() {
        return new ReportData(id, sessionExecutionId, painLevel, difficultyLevel, techniqueConfidenceLevel, note,
                sessionRpe, observationMode, reportedAt);
    }
}

@Entity(name = "ExecutionCorrectionJpaEntity")
@Table(name = "execution_correction", schema = "training_execution")
class ExecutionCorrectionJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id", nullable = false) UUID sessionExecutionId;
    @Column(name = "corrected_by_account_id", nullable = false) UUID correctedByAccountId;
    @Column(nullable = false, length = 500) String reason;
    @Column(name = "corrected_pain_level") Integer correctedPainLevel;
    @Column(name = "corrected_difficulty_level") Integer correctedDifficultyLevel;
    @Column(name = "corrected_result_id") UUID correctedResultId;
    @Column(name = "corrected_sets") Integer correctedSets;
    @Column(name = "corrected_repetitions") Integer correctedRepetitions;
    @Column(name = "corrected_duration_seconds") Integer correctedDurationSeconds;
    @Column(name = "corrected_contacts") Integer correctedContacts;
    @Column(name = "corrected_external_load_value") BigDecimal correctedExternalLoadValue;
    @Column(name = "corrected_external_load_unit") String correctedExternalLoadUnit;
    @Column(name = "corrected_side") String correctedSide;
    @Column(name = "corrected_modified") Boolean correctedModified;
    @Column(name = "corrected_skipped") Boolean correctedSkipped;
    @Column(name = "observation_mode") String observationMode;
    @Column(name = "idempotency_key") String idempotencyKey;
    @Column(name = "corrected_at", nullable = false, updatable = false) Instant correctedAt;

    protected ExecutionCorrectionJpaEntity() {
    }

    ExecutionCorrectionJpaEntity(CorrectionData source) {
        id = source.id();
        sessionExecutionId = source.sessionExecutionId();
        correctedByAccountId = source.correctedByAccountId();
        reason = source.reason();
        correctedPainLevel = source.correctedPainLevel();
        correctedDifficultyLevel = source.correctedDifficultyLevel();
        correctedResultId = source.correctedResultId(); correctedSets = source.correctedSets();
        correctedRepetitions = source.correctedRepetitions();
        correctedDurationSeconds = source.correctedDurationSeconds(); correctedContacts = source.correctedContacts();
        correctedExternalLoadValue = source.correctedExternalLoadValue();
        correctedExternalLoadUnit = source.correctedExternalLoadUnit(); correctedSide = source.correctedSide();
        correctedModified = source.correctedModified(); correctedSkipped = source.correctedSkipped();
        observationMode = source.observationMode(); idempotencyKey = source.idempotencyKey();
        correctedAt = source.correctedAt();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    CorrectionData data() {
        return new CorrectionData(id, sessionExecutionId, correctedByAccountId, reason,
                correctedPainLevel, correctedDifficultyLevel, correctedResultId, correctedSets,
                correctedRepetitions, correctedDurationSeconds, correctedContacts,
                correctedExternalLoadValue, correctedExternalLoadUnit, correctedSide,
                correctedModified, correctedSkipped, observationMode, idempotencyKey, correctedAt);
    }
}

@Entity(name = "ExecutionAlertJpaEntity")
@Table(name = "execution_alert", schema = "training_execution")
class ExecutionAlertJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id", nullable = false) UUID sessionExecutionId;
    @Column(name = "alert_type", nullable = false, length = 64) String alertType;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    String priority;
    @Column(name = "owner_account_id") UUID ownerAccountId;
    String status;
    @Column(name = "due_at") Instant dueAt;
    @Column(name = "source_response_id") UUID sourceResponseId;
    @Column(name = "acknowledged_at") Instant acknowledgedAt;
    @Column(name = "resolved_at") Instant resolvedAt;

    protected ExecutionAlertJpaEntity() {
    }

    ExecutionAlertJpaEntity(AlertData source) {
        id = source.id();
        sessionExecutionId = source.sessionExecutionId();
        alertType = source.alertType();
        priority = source.priority();
        ownerAccountId = source.ownerAccountId();
        status = source.status();
        dueAt = source.dueAt();
        sourceResponseId = source.sourceResponseId();
        createdAt = source.createdAt();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    AlertData data() {
        return new AlertData(id, sessionExecutionId, alertType, priority, ownerAccountId,
                status, dueAt, sourceResponseId, createdAt);
    }
}

@Entity(name = "ExecutedLoadObservationJpaEntity")
@Table(name = "executed_load_observation", schema = "training_execution")
class ExecutedLoadObservationJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id") UUID executionId;
    @Column(name = "result_id") UUID resultId;
    @Column(name = "exercise_version_id") UUID exerciseVersionId;
    @Column(name = "anatomical_structure_id") UUID structureId;
    String side;
    String channel;
    String unit;
    @Column(name = "value_low") BigDecimal low;
    @Column(name = "value_high") BigDecimal high;
    @Column(name = "observation_mode") String observationMode;
    @Column(name = "calculator_version") String calculatorVersion;
    @Column(name = "observed_at") Instant observedAt;

    protected ExecutedLoadObservationJpaEntity() {
    }

    ExecutedLoadObservationJpaEntity(ExecutedObservationData source) {
        id = source.id(); executionId = source.executionId(); resultId = source.resultId();
        exerciseVersionId = source.exerciseVersionId(); structureId = source.anatomicalStructureId();
        side = source.side(); channel = source.channel(); unit = source.unit();
        low = source.low(); high = source.high(); observationMode = source.observationMode();
        calculatorVersion = source.calculatorVersion(); observedAt = source.observedAt();
    }
}

@Entity(name = "ExecutionProjectionReceiptJpaEntity")
@Table(name = "execution_projection_receipt", schema = "training_execution")
class ExecutionProjectionReceiptJpaEntity {
    @Id @Column(name = "execution_id") UUID executionId;
    @Column(name = "processed_at") Instant processedAt;
    int attempts;

    protected ExecutionProjectionReceiptJpaEntity() {
    }
}

@Entity(name = "ExecutionQualificationJpaEntity")
@Table(name = "execution_qualification", schema = "training_execution")
class ExecutionQualificationJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id") UUID executionId;
    @Column(name = "qualification_type") String qualificationType;
    String status;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "reversed_at") Instant reversedAt;

    protected ExecutionQualificationJpaEntity() {
    }
}

@Entity(name = "Post24hResponseJpaEntity")
@Table(name = "post_24h_response", schema = "training_execution")
class Post24hResponseJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id") UUID executionId;
    @Column(name = "participant_account_id") UUID participantId;
    @Column(name = "pain_level") int painLevel;
    @Column(name = "difficulty_level") int difficultyLevel;
    String note;
    @Column(name = "observation_mode") String observationMode;
    @Column(name = "idempotency_key") String idempotencyKey;
    @Column(name = "reported_at") Instant reportedAt;

    protected Post24hResponseJpaEntity() {
    }

    Post24hResponseJpaEntity(Post24hData source) {
        id = source.id(); executionId = source.executionId(); participantId = source.participantId();
        painLevel = source.painLevel(); difficultyLevel = source.difficultyLevel(); note = source.note();
        observationMode = source.observationMode(); idempotencyKey = source.idempotencyKey();
        reportedAt = source.reportedAt();
    }

    Post24hData data() {
        return new Post24hData(id, executionId, participantId, painLevel, difficultyLevel,
                note, observationMode, idempotencyKey, reportedAt);
    }
}

@Entity(name = "ExecutionAlertHistoryJpaEntity")
@Table(name = "execution_alert_history", schema = "training_execution")
class ExecutionAlertHistoryJpaEntity {
    @Id UUID id;
    @Column(name = "alert_id") UUID alertId;
    @Column(name = "actor_account_id") UUID actorId;
    String action;
    @Column(name = "comment_reference") String commentReference;
    @Column(name = "occurred_at") Instant occurredAt;

    protected ExecutionAlertHistoryJpaEntity() {
    }
}
