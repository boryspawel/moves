package com.motionecosystem.trainingexecution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable participant declaration; the highest revision is the current fact. */
@Entity
@Table(name = "session_execution_attempt_fact", schema = "training_execution")
class SessionExecutionAttemptFact {
    @Id UUID id;
    @Column(name = "attempt_id", nullable = false) UUID attemptId;
    @Column(name = "exercise_prescription_id", nullable = false) UUID exercisePrescriptionId;
    @Column(name = "revision_number", nullable = false) int revisionNumber;
    @Column(nullable = false) String outcome;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "result_payload", nullable = false, columnDefinition = "jsonb") String resultPayload;
    @Column(name = "recorded_at", nullable = false, updatable = false) Instant recordedAt;

    protected SessionExecutionAttemptFact() { }
    SessionExecutionAttemptFact(UUID attemptId, UUID prescriptionId, int revisionNumber, String outcome, String payload, Instant now) {
        id = UUID.randomUUID(); this.attemptId = attemptId; exercisePrescriptionId = prescriptionId;
        this.revisionNumber = revisionNumber; this.outcome = outcome; resultPayload = payload; recordedAt = now;
    }
}
