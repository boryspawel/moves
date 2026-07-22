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

    java.util.Set<UUID> findDeclaredSessionIds(UUID participantAccountId, java.util.Collection<UUID> plannedSessionIds);

    void save(ExecutionData execution, List<ResultData> results, ReportData report, List<AlertData> alerts);

    void appendCorrection(CorrectionData correction);

    Optional<CorrectionData> findCorrectionByIdempotencyKey(UUID executionId, String idempotencyKey);

    boolean saveProjection(UUID executionId, List<ExecutedObservationData> observations,
                           Instant processedAt);

    boolean rebuildProjection(UUID executionId, List<ExecutedObservationData> observations,
                              Instant processedAt);

    List<UUID> findProjectionCandidates(Instant recordedBefore);

    void markProjectionFailed(UUID executionId);

    Post24hData savePost24h(Post24hData response);

    Optional<Post24hData> findPost24h(UUID executionId, String idempotencyKey);

    Optional<AlertData> transitionAlert(UUID alertId, UUID actorId, String action,
                                        UUID assignedOwnerId, String commentReference, Instant now);

    boolean reverseQualification(UUID executionId, Instant now);

    boolean hasActiveQualification(UUID executionId, String qualificationType);

    record ExecutionData(UUID id, UUID plannedSessionId, UUID participantAccountId,
                         boolean declaredCompletion, String idempotencyKey, Instant recordedAt,
                         UUID declarationEventId, String projectionStatus) {
    }

    record ResultData(UUID id, UUID sessionExecutionId, UUID exercisePrescriptionId,
                      UUID exerciseVersionId, Integer actualSets, Integer actualRepetitions,
                      Integer actualDurationSeconds, Integer actualContacts, BigDecimal actualDistanceMeters,
                      BigDecimal actualLoadKg, BigDecimal actualExternalLoadValue,
                      String actualExternalLoadUnit, String actualIntensityType,
                      BigDecimal actualIntensityValue, String actualIntensityZone, String side,
                      boolean modified, boolean skipped, String observationMode) {
    }

    record ReportData(UUID id, UUID sessionExecutionId, int painLevel, int difficultyLevel,
                      Integer techniqueConfidenceLevel, String note, Integer sessionRpe, String observationMode, Instant reportedAt) {
    }

    record CorrectionData(UUID id, UUID sessionExecutionId, UUID correctedByAccountId,
                          String reason, Integer correctedPainLevel, Integer correctedDifficultyLevel,
                          UUID correctedResultId, Integer correctedSets, Integer correctedRepetitions,
                          Integer correctedDurationSeconds, Integer correctedContacts,
                          BigDecimal correctedExternalLoadValue, String correctedExternalLoadUnit,
                          String correctedSide, Boolean correctedModified, Boolean correctedSkipped,
                          String observationMode, String idempotencyKey, Instant correctedAt) {
    }

    record AlertData(UUID id, UUID sessionExecutionId, String alertType, String priority,
                     UUID ownerAccountId, String status, Instant dueAt, UUID sourceResponseId,
                     Instant createdAt) {
        public AlertData(UUID id, UUID executionId, String type, Instant createdAt) {
            this(id, executionId, type, "MEDIUM", null, "OPEN",
                    createdAt.plusSeconds(24 * 60 * 60), null, createdAt);
        }
    }

    record ExecutedObservationData(
            UUID id, UUID executionId, UUID resultId, UUID exerciseVersionId,
            UUID anatomicalStructureId, String side, String channel, String unit,
            BigDecimal low, BigDecimal high, String observationMode,
            String calculatorVersion, Instant observedAt) {
    }

    record Post24hData(
            UUID id, UUID executionId, UUID participantId, int painLevel, int difficultyLevel,
            String note, String observationMode, String idempotencyKey, Instant reportedAt) {
    }

    record ExecutionAggregate(ExecutionData execution, List<ResultData> results, ReportData report,
                              List<CorrectionData> corrections, List<AlertData> alerts) {
    }

    record ExecutionOwner(UUID executionId, UUID participantAccountId) {
    }
}
