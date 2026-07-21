package com.motionecosystem.trainingexecution.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.motionecosystem.trainingexecution.SessionExecutionPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaSessionExecutionAdapter implements SessionExecutionPersistence {

    private final EntityManager entityManager;

    @Override
    public Optional<ExecutionAggregate> findByParticipantAndIdempotencyKey(UUID participantAccountId,
                                                                           String idempotencyKey) {
        return entityManager.createQuery("""
                SELECT execution FROM SessionExecutionJpaEntity execution
                WHERE execution.participantAccountId = :participantAccountId
                  AND execution.idempotencyKey = :idempotencyKey
                """, SessionExecutionJpaEntity.class)
                .setParameter("participantAccountId", participantAccountId)
                .setParameter("idempotencyKey", idempotencyKey)
                .getResultStream().findFirst().map(this::aggregate);
    }

    @Override
    public Optional<ExecutionAggregate> findByPlannedSessionId(UUID plannedSessionId) {
        return entityManager.createQuery("""
                SELECT execution FROM SessionExecutionJpaEntity execution
                WHERE execution.plannedSessionId = :plannedSessionId
                ORDER BY execution.recordedAt, execution.id
                """, SessionExecutionJpaEntity.class)
                .setParameter("plannedSessionId", plannedSessionId)
                .setMaxResults(1)
                .getResultStream().findFirst().map(this::aggregate);
    }

    @Override
    public Optional<ExecutionOwner> findOwner(UUID executionId) {
        return Optional.ofNullable(entityManager.find(SessionExecutionJpaEntity.class, executionId))
                .map(item -> new ExecutionOwner(item.id(), item.participantAccountId()));
    }

    @Override
    public Optional<ExecutionAggregate> findById(UUID executionId) {
        return Optional.ofNullable(entityManager.find(SessionExecutionJpaEntity.class, executionId))
                .map(this::aggregate);
    }

    @Override
    public List<ExecutionAggregate> findByParticipant(UUID participantAccountId) {
        List<SessionExecutionJpaEntity> executions = entityManager.createQuery("""
                SELECT execution FROM SessionExecutionJpaEntity execution
                WHERE execution.participantAccountId = :participantAccountId
                ORDER BY execution.recordedAt DESC, execution.id
                """, SessionExecutionJpaEntity.class)
                .setParameter("participantAccountId", participantAccountId)
                .getResultList();
        if (executions.isEmpty()) {
            return List.of();
        }
        return aggregates(executions);
    }

    @Override
    public void save(ExecutionData execution, List<ResultData> results,
                     ReportData report, List<AlertData> alerts) {
        entityManager.persist(new SessionExecutionJpaEntity(execution));
        results.stream().map(ExerciseResultJpaEntity::new).forEach(entityManager::persist);
        entityManager.persist(new PainDifficultyReportJpaEntity(report));
        alerts.stream().map(ExecutionAlertJpaEntity::new).forEach(entityManager::persist);
        entityManager.flush();
    }

    @Override
    public void appendCorrection(CorrectionData correction) {
        entityManager.persist(new ExecutionCorrectionJpaEntity(correction));
        entityManager.flush();
    }

    @Override
    public Optional<CorrectionData> findCorrectionByIdempotencyKey(UUID executionId, String idempotencyKey) {
        return entityManager.createQuery("""
                SELECT correction FROM ExecutionCorrectionJpaEntity correction
                WHERE correction.sessionExecutionId=:executionId AND correction.idempotencyKey=:key
                """, ExecutionCorrectionJpaEntity.class)
                .setParameter("executionId", executionId).setParameter("key", idempotencyKey)
                .getResultStream().findFirst().map(ExecutionCorrectionJpaEntity::data);
    }

    @Override
    public boolean saveProjection(
            UUID executionId, List<ExecutedObservationData> observations, Instant processedAt) {
        return persistProjection(executionId, observations, processedAt, false);
    }

    @Override
    public boolean rebuildProjection(
            UUID executionId, List<ExecutedObservationData> observations, Instant processedAt) {
        return persistProjection(executionId, observations, processedAt, true);
    }

    private boolean persistProjection(
            UUID executionId, List<ExecutedObservationData> observations, Instant processedAt,
            boolean rebuild) {
        SessionExecutionJpaEntity execution = entityManager.find(
                SessionExecutionJpaEntity.class, executionId, LockModeType.PESSIMISTIC_WRITE);
        if (execution == null) throw new IllegalArgumentException("execution not found");
        ExecutionProjectionReceiptJpaEntity previous = entityManager.find(
                ExecutionProjectionReceiptJpaEntity.class, executionId);
        if (previous != null && !rebuild) {
            return false;
        }
        if (previous != null) {
            entityManager.createQuery("""
                    DELETE FROM ExecutedLoadObservationJpaEntity observation
                    WHERE observation.executionId=:executionId
                    """).setParameter("executionId", executionId).executeUpdate();
            entityManager.remove(previous);
            entityManager.flush();
        }
        observations.stream().map(ExecutedLoadObservationJpaEntity::new).forEach(entityManager::persist);
        ExecutionProjectionReceiptJpaEntity receipt = new ExecutionProjectionReceiptJpaEntity();
        receipt.executionId = executionId;
        receipt.processedAt = processedAt;
        receipt.attempts = 1;
        entityManager.persist(receipt);
        if (!rebuild) {
            ExecutionQualificationJpaEntity qualification = new ExecutionQualificationJpaEntity();
            qualification.id = UUID.randomUUID();
            qualification.executionId = executionId;
            qualification.qualificationType = "COMPLETED_SESSION";
            qualification.status = "QUALIFIED";
            qualification.createdAt = processedAt;
            entityManager.persist(qualification);
        }
        execution.projectionStatus = "PROJECTED";
        for (int days : List.of(7, 14, 28)) {
            entityManager.createNativeQuery("""
                    INSERT INTO training_execution.executed_load_aggregate
                        (id, participant_account_id, window_days, window_end,
                         anatomical_structure_id, side, channel, unit,
                         value_low, value_high, rebuilt_at)
                    SELECT gen_random_uuid(), execution.participant_account_id, :days, :windowEnd,
                           observation.anatomical_structure_id, observation.side,
                           observation.channel, observation.unit,
                           SUM(observation.value_low), SUM(observation.value_high), :windowEnd
                    FROM training_execution.executed_load_observation observation
                    JOIN training_execution.session_execution execution
                      ON execution.id = observation.session_execution_id
                    WHERE execution.participant_account_id = :participant
                      AND observation.observed_at > :windowStart
                      AND observation.observed_at <= :windowEnd
                    GROUP BY execution.participant_account_id, observation.anatomical_structure_id,
                             observation.side, observation.channel, observation.unit
                    ON CONFLICT (participant_account_id, window_days, window_end,
                                 anatomical_structure_id, side, channel, unit)
                    DO UPDATE SET value_low=EXCLUDED.value_low, value_high=EXCLUDED.value_high,
                                  rebuilt_at=EXCLUDED.rebuilt_at
                    """)
                    .setParameter("days", days)
                    .setParameter("windowEnd", processedAt)
                    .setParameter("windowStart", processedAt.minusSeconds(days * 24L * 60L * 60L))
                    .setParameter("participant", execution.participantAccountId)
                    .executeUpdate();
        }
        entityManager.flush();
        return true;
    }

    @Override
    public List<UUID> findProjectionCandidates(Instant recordedBefore) {
        return entityManager.createQuery("""
                SELECT execution.id FROM SessionExecutionJpaEntity execution
                WHERE execution.projectionStatus IN ('PENDING', 'FAILED')
                  AND execution.recordedAt <= :recordedBefore
                ORDER BY execution.recordedAt, execution.id
                """, UUID.class).setParameter("recordedBefore", recordedBefore).getResultList();
    }

    @Override
    public void markProjectionFailed(UUID executionId) {
        entityManager.createQuery("""
                UPDATE SessionExecutionJpaEntity execution SET execution.projectionStatus='FAILED'
                WHERE execution.id=:executionId AND execution.projectionStatus<>'PROJECTED'
                """).setParameter("executionId", executionId).executeUpdate();
    }

    @Override
    public Post24hData savePost24h(Post24hData response) {
        entityManager.persist(new Post24hResponseJpaEntity(response));
        if (response.painLevel() > 0) {
            AlertData alert = new AlertData(UUID.randomUUID(), response.executionId(),
                    "POST_24H_PAIN", response.painLevel() >= 7 ? "HIGH" : "MEDIUM",
                    null, "OPEN", response.reportedAt().plusSeconds(12 * 60 * 60),
                    response.id(), response.reportedAt());
            entityManager.persist(new ExecutionAlertJpaEntity(alert));
        }
        if (response.difficultyLevel() >= 8) {
            AlertData alert = new AlertData(UUID.randomUUID(), response.executionId(),
                    "POST_24H_DIFFICULTY", "MEDIUM", null, "OPEN",
                    response.reportedAt().plusSeconds(24 * 60 * 60), response.id(), response.reportedAt());
            entityManager.persist(new ExecutionAlertJpaEntity(alert));
        }
        entityManager.flush();
        return response;
    }

    @Override
    public Optional<Post24hData> findPost24h(UUID executionId, String idempotencyKey) {
        return entityManager.createQuery("""
                SELECT response FROM Post24hResponseJpaEntity response
                WHERE response.executionId=:executionId AND response.idempotencyKey=:key
                """, Post24hResponseJpaEntity.class)
                .setParameter("executionId", executionId).setParameter("key", idempotencyKey)
                .getResultStream().findFirst().map(Post24hResponseJpaEntity::data);
    }

    @Override
    public Optional<AlertData> transitionAlert(
            UUID alertId, UUID actorId, String action, UUID assignedOwnerId,
            String commentReference, Instant now) {
        ExecutionAlertJpaEntity alert = entityManager.find(
                ExecutionAlertJpaEntity.class, alertId, LockModeType.PESSIMISTIC_WRITE);
        if (alert == null) return Optional.empty();
        switch (action) {
            case "ACKNOWLEDGE" -> {
                requireStatus(alert, "OPEN", "REOPENED");
                alert.status = "ACKNOWLEDGED"; alert.acknowledgedAt = now;
            }
            case "RESOLVE" -> {
                requireStatus(alert, "OPEN", "ACKNOWLEDGED", "REOPENED");
                alert.status = "RESOLVED"; alert.resolvedAt = now;
            }
            case "REOPEN" -> {
                requireStatus(alert, "RESOLVED");
                alert.status = "REOPENED"; alert.resolvedAt = null;
            }
            case "ASSIGN" -> {
                if (assignedOwnerId == null) throw new IllegalArgumentException("alert owner is required");
                alert.ownerAccountId = assignedOwnerId;
            }
            default -> throw new IllegalArgumentException("unsupported alert action");
        }
        ExecutionAlertHistoryJpaEntity history = new ExecutionAlertHistoryJpaEntity();
        history.id = UUID.randomUUID(); history.alertId = alertId; history.actorId = actorId;
        history.action = action; history.commentReference = commentReference; history.occurredAt = now;
        entityManager.persist(history);
        return Optional.of(alert.data());
    }

    private static void requireStatus(ExecutionAlertJpaEntity alert, String... allowed) {
        if (!java.util.Set.of(allowed).contains(alert.status)) {
            throw new IllegalStateException("alert transition is not allowed from current status");
        }
    }

    @Override
    public boolean reverseQualification(UUID executionId, Instant now) {
        return entityManager.createQuery("""
                UPDATE ExecutionQualificationJpaEntity qualification
                SET qualification.status='REVERSED', qualification.reversedAt=:now
                WHERE qualification.executionId=:executionId AND qualification.status='QUALIFIED'
                """).setParameter("now", now).setParameter("executionId", executionId).executeUpdate() == 1;
    }

    @Override
    public boolean hasActiveQualification(UUID executionId, String qualificationType) {
        boolean qualified = entityManager.createQuery("""
                SELECT COUNT(qualification.id) FROM ExecutionQualificationJpaEntity qualification
                WHERE qualification.executionId=:executionId
                  AND qualification.qualificationType=:type AND qualification.status='QUALIFIED'
                """, Long.class).setParameter("executionId", executionId)
                .setParameter("type", qualificationType).getSingleResult() == 1;
        if (qualified) return true;
        SessionExecutionJpaEntity legacy = entityManager.find(SessionExecutionJpaEntity.class, executionId);
        return legacy != null && legacy.declarationEventId == null;
    }

    private ExecutionAggregate aggregate(SessionExecutionJpaEntity execution) {
        return aggregates(List.of(execution)).getFirst();
    }

    private List<ExecutionAggregate> aggregates(List<SessionExecutionJpaEntity> executions) {
        List<UUID> ids = executions.stream().map(SessionExecutionJpaEntity::id).toList();
        Map<UUID, List<ExerciseResultJpaEntity>> results = groupedResults(ids);
        Map<UUID, PainDifficultyReportJpaEntity> reports = reports(ids);
        Map<UUID, List<ExecutionCorrectionJpaEntity>> corrections = groupedCorrections(ids);
        Map<UUID, List<ExecutionAlertJpaEntity>> alerts = groupedAlerts(ids);
        return executions.stream().map(execution -> new ExecutionAggregate(
                execution.data(),
                results.getOrDefault(execution.id(), List.of()).stream()
                        .map(ExerciseResultJpaEntity::data).toList(),
                Optional.ofNullable(reports.get(execution.id()))
                        .orElseThrow(() -> new IllegalStateException("execution report is missing"))
                        .data(),
                corrections.getOrDefault(execution.id(), List.of()).stream()
                        .map(ExecutionCorrectionJpaEntity::data).toList(),
                alerts.getOrDefault(execution.id(), List.of()).stream()
                        .map(ExecutionAlertJpaEntity::data).toList()))
                .toList();
    }

    private Map<UUID, List<ExerciseResultJpaEntity>> groupedResults(List<UUID> ids) {
        return entityManager.createQuery("""
                SELECT result FROM ExerciseResultJpaEntity result
                WHERE result.sessionExecutionId IN :ids
                ORDER BY result.sessionExecutionId, result.exercisePrescriptionId
                """, ExerciseResultJpaEntity.class)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.groupingBy(ExerciseResultJpaEntity::sessionExecutionId));
    }

    private Map<UUID, PainDifficultyReportJpaEntity> reports(List<UUID> ids) {
        return entityManager.createQuery("""
                SELECT report FROM PainDifficultyReportJpaEntity report
                WHERE report.sessionExecutionId IN :ids
                """, PainDifficultyReportJpaEntity.class)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(PainDifficultyReportJpaEntity::sessionExecutionId, Function.identity()));
    }

    private Map<UUID, List<ExecutionCorrectionJpaEntity>> groupedCorrections(List<UUID> ids) {
        return entityManager.createQuery("""
                SELECT correction FROM ExecutionCorrectionJpaEntity correction
                WHERE correction.sessionExecutionId IN :ids
                ORDER BY correction.sessionExecutionId, correction.correctedAt, correction.id
                """, ExecutionCorrectionJpaEntity.class)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.groupingBy(ExecutionCorrectionJpaEntity::sessionExecutionId));
    }

    private Map<UUID, List<ExecutionAlertJpaEntity>> groupedAlerts(List<UUID> ids) {
        return entityManager.createQuery("""
                SELECT alert FROM ExecutionAlertJpaEntity alert
                WHERE alert.sessionExecutionId IN :ids
                ORDER BY alert.sessionExecutionId, alert.alertType
                """, ExecutionAlertJpaEntity.class)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.groupingBy(ExecutionAlertJpaEntity::sessionExecutionId));
    }
}
