package com.motionecosystem.trainingexecution.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.motionecosystem.trainingexecution.SessionExecutionPersistence;
import jakarta.persistence.EntityManager;
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
