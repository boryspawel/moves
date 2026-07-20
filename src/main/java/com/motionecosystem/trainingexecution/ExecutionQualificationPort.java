package com.motionecosystem.trainingexecution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ExecutionQualificationPort {

    private final JdbcTemplate jdbc;

    public ExecutionQualificationPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public QualifyingExecution requireDeclared(UUID executionId) {
        List<QualifyingExecution> executions = jdbc.query("""
                SELECT execution.id, execution.participant_account_id, execution.recorded_at,
                       execution.declared_completion,
                       string_agg(prescription.exercise_version_id::text, ',' ORDER BY prescription.position)
                           AS activity_key
                FROM training_execution.session_execution execution
                JOIN training_planning.exercise_prescription prescription
                  ON prescription.planned_session_id = execution.planned_session_id
                WHERE execution.id = ?
                GROUP BY execution.id
                """, (rs, row) -> new QualifyingExecution(
                rs.getObject("id", UUID.class),
                rs.getObject("participant_account_id", UUID.class),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getBoolean("declared_completion"),
                rs.getString("activity_key")), executionId);
        if (executions.size() != 1 || !executions.getFirst().declaredCompletion()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "qualifying declared execution not found");
        }
        return executions.getFirst();
    }

    public record QualifyingExecution(UUID executionId, UUID participantAccountId,
                                      Instant recordedAt, boolean declaredCompletion,
                                      String activityKey) {
    }
}
