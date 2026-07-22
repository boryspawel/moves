package com.motionecosystem.trainingexecution;

import com.motionecosystem.trainingexecution.api.SessionExecutionAttemptQueryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SessionExecutionAttemptQueryAdapter implements SessionExecutionAttemptQueryPort {
    private final SessionExecutionAttemptRepository attempts;

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptSnapshot> findOwnedAttempt(UUID participantAccountId, UUID attemptId) {
        return attempts.findById(attemptId)
                .filter(attempt -> participantAccountId.equals(attempt.participantAccountId))
                .map(attempt -> new AttemptSnapshot(attempt.id, attempt.participantAccountId, attempt.plannedSessionId,
                        attempt.planRevisionId, attempt.status));
    }
}
