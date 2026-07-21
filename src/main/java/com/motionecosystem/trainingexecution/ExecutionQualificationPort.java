package com.motionecosystem.trainingexecution;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import com.motionecosystem.trainingplanning.api.PlannedSessionExecutionPort;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class ExecutionQualificationPort {

    private final SessionExecutionPersistence executions;
    private final PlannedSessionExecutionPort plannedSessions;

    public QualifyingExecution requireDeclared(UUID executionId) {
        var execution = executions.findById(executionId)
                .map(SessionExecutionPersistence.ExecutionAggregate::execution)
                .filter(SessionExecutionPersistence.ExecutionData::declaredCompletion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "qualifying declared execution not found"));
        var session = plannedSessions.findSession(execution.plannedSessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "qualifying planned session not found"));
        String activityKey = session.prescriptions().stream()
                .map(item -> item.exerciseVersionId().toString())
                .collect(Collectors.joining(","));
        if (activityKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "qualifying declared execution not found");
        }
        return new QualifyingExecution(execution.id(), execution.participantAccountId(),
                execution.recordedAt(), true, activityKey);
    }

    public record QualifyingExecution(UUID executionId, UUID participantAccountId,
                                      Instant recordedAt, boolean declaredCompletion,
                                      String activityKey) {
    }
}
