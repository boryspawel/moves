package com.motionecosystem.trainingexecution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class ExecutionQualificationPort {

    private final ExecutionQualificationRepository executions;

    public QualifyingExecution requireDeclared(UUID executionId) {
        List<QualifyingExecution> matches = executions.findByExecutionId(executionId);
        if (matches.size() != 1 || !matches.getFirst().declaredCompletion()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "qualifying declared execution not found");
        }
        return matches.getFirst();
    }

    public record QualifyingExecution(UUID executionId, UUID participantAccountId,
                                      Instant recordedAt, boolean declaredCompletion,
                                      String activityKey) {
    }
}
