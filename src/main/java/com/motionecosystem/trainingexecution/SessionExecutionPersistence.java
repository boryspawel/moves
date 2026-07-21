package com.motionecosystem.trainingexecution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application persistence port for the append-only execution aggregate. */
public interface SessionExecutionPersistence {

    Optional<ExecutionAggregate> findByParticipantAndIdempotencyKey(UUID participantAccountId,
                                                                    String idempotencyKey);

    Optional<ExecutionAggregate> findByPlannedSessionId(UUID plannedSessionId);

    Optional<ExecutionOwner> findOwner(UUID executionId);

    Optional<ExecutionAggregate> findById(UUID executionId);

    List<ExecutionAggregate> findByParticipant(UUID participantAccountId);

    void save(ExecutionData execution, List<ResultData> results, ReportData report, List<AlertData> alerts);

    void appendCorrection(CorrectionData correction);

    record ExecutionData(UUID id, UUID plannedSessionId, UUID participantAccountId,
                         boolean declaredCompletion, String idempotencyKey, Instant recordedAt) {
    }

    record ResultData(UUID id, UUID sessionExecutionId, UUID exercisePrescriptionId,
                      Integer actualRepetitions, Integer actualDurationSeconds, BigDecimal actualLoadKg) {
    }

    record ReportData(UUID id, UUID sessionExecutionId, int painLevel, int difficultyLevel,
                      String note, Instant reportedAt) {
    }

    record CorrectionData(UUID id, UUID sessionExecutionId, UUID correctedByAccountId,
                          String reason, Integer correctedPainLevel, Integer correctedDifficultyLevel,
                          Instant correctedAt) {
    }

    record AlertData(UUID id, UUID sessionExecutionId, String alertType, Instant createdAt) {
    }

    record ExecutionAggregate(ExecutionData execution, List<ResultData> results, ReportData report,
                              List<CorrectionData> corrections, List<AlertData> alerts) {
    }

    record ExecutionOwner(UUID executionId, UUID participantAccountId) {
    }
}
