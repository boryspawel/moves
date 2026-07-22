package com.motionecosystem.trainingexecution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_execution_attempt_progress", schema = "training_execution")
class SessionExecutionAttemptProgress {
    @Id UUID id;
    @Column(name = "attempt_id", nullable = false) UUID attemptId;
    @Column(name = "exercise_prescription_id", nullable = false) UUID exercisePrescriptionId;
    @Column(nullable = false) boolean completed;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    protected SessionExecutionAttemptProgress() { }

    SessionExecutionAttemptProgress(UUID attemptId, UUID exercisePrescriptionId, boolean completed, Instant now) {
        id = UUID.randomUUID(); this.attemptId = attemptId; this.exercisePrescriptionId = exercisePrescriptionId;
        this.completed = completed; updatedAt = now;
    }

    void update(boolean value, Instant now) { completed = value; updatedAt = now; }
}
