package com.motionecosystem.trainingexecution.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motionecosystem.trainingexecution.SessionExecutionPersistence.AlertData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.CorrectionData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ExecutionData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ReportData;
import com.motionecosystem.trainingexecution.SessionExecutionPersistence.ResultData;
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

    protected SessionExecutionJpaEntity() {
    }

    SessionExecutionJpaEntity(ExecutionData source) {
        id = source.id();
        plannedSessionId = source.plannedSessionId();
        participantAccountId = source.participantAccountId();
        declaredCompletion = source.declaredCompletion();
        idempotencyKey = source.idempotencyKey();
        recordedAt = source.recordedAt();
    }

    UUID id() { return id; }
    UUID participantAccountId() { return participantAccountId; }

    ExecutionData data() {
        return new ExecutionData(id, plannedSessionId, participantAccountId,
                declaredCompletion, idempotencyKey, recordedAt);
    }
}

@Entity(name = "ExerciseResultJpaEntity")
@Table(name = "exercise_result", schema = "training_execution")
class ExerciseResultJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id", nullable = false) UUID sessionExecutionId;
    @Column(name = "exercise_prescription_id", nullable = false) UUID exercisePrescriptionId;
    @Column(name = "actual_repetitions") Integer actualRepetitions;
    @Column(name = "actual_duration_seconds") Integer actualDurationSeconds;
    @Column(name = "actual_load_kg", precision = 8, scale = 2) BigDecimal actualLoadKg;

    protected ExerciseResultJpaEntity() {
    }

    ExerciseResultJpaEntity(ResultData source) {
        id = source.id();
        sessionExecutionId = source.sessionExecutionId();
        exercisePrescriptionId = source.exercisePrescriptionId();
        actualRepetitions = source.actualRepetitions();
        actualDurationSeconds = source.actualDurationSeconds();
        actualLoadKg = source.actualLoadKg();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    ResultData data() {
        return new ResultData(id, sessionExecutionId, exercisePrescriptionId,
                actualRepetitions, actualDurationSeconds, actualLoadKg);
    }
}

@Entity(name = "PainDifficultyReportJpaEntity")
@Table(name = "pain_difficulty_report", schema = "training_execution")
class PainDifficultyReportJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id", nullable = false, unique = true) UUID sessionExecutionId;
    @Column(name = "pain_level", nullable = false) int painLevel;
    @Column(name = "difficulty_level", nullable = false) int difficultyLevel;
    @Column(length = 500) String note;
    @Column(name = "reported_at", nullable = false, updatable = false) Instant reportedAt;

    protected PainDifficultyReportJpaEntity() {
    }

    PainDifficultyReportJpaEntity(ReportData source) {
        id = source.id();
        sessionExecutionId = source.sessionExecutionId();
        painLevel = source.painLevel();
        difficultyLevel = source.difficultyLevel();
        note = source.note();
        reportedAt = source.reportedAt();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    ReportData data() {
        return new ReportData(id, sessionExecutionId, painLevel, difficultyLevel, note, reportedAt);
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
        correctedAt = source.correctedAt();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    CorrectionData data() {
        return new CorrectionData(id, sessionExecutionId, correctedByAccountId, reason,
                correctedPainLevel, correctedDifficultyLevel, correctedAt);
    }
}

@Entity(name = "ExecutionAlertJpaEntity")
@Table(name = "execution_alert", schema = "training_execution")
class ExecutionAlertJpaEntity {
    @Id UUID id;
    @Column(name = "session_execution_id", nullable = false) UUID sessionExecutionId;
    @Column(name = "alert_type", nullable = false, length = 64) String alertType;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

    protected ExecutionAlertJpaEntity() {
    }

    ExecutionAlertJpaEntity(AlertData source) {
        id = source.id();
        sessionExecutionId = source.sessionExecutionId();
        alertType = source.alertType();
        createdAt = source.createdAt();
    }

    UUID sessionExecutionId() { return sessionExecutionId; }

    AlertData data() {
        return new AlertData(id, sessionExecutionId, alertType, createdAt);
    }
}
