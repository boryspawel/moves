package com.motionecosystem.trainingexecution;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ExecutionQualificationRepository {

    private final EntityManager entityManager;

    List<ExecutionQualificationPort.QualifyingExecution> findByExecutionId(UUID executionId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT execution.id, execution.participant_account_id, execution.recorded_at,
                       execution.declared_completion,
                       string_agg(prescription.exercise_version_id::text, ',' ORDER BY prescription.position)
                           AS activity_key
                FROM training_execution.session_execution execution
                JOIN training_planning.exercise_prescription prescription
                  ON prescription.planned_session_id = execution.planned_session_id
                WHERE execution.id = :executionId
                GROUP BY execution.id
                """).setParameter("executionId", executionId).getResultList();
        return rows.stream().map(row -> new ExecutionQualificationPort.QualifyingExecution(
                (UUID) row[0], (UUID) row[1], instant(row[2]), (Boolean) row[3], (String) row[4])).toList();
    }

    private static Instant instant(Object value) {
        return value instanceof OffsetDateTime timestamp ? timestamp.toInstant() : (Instant) value;
    }
}
