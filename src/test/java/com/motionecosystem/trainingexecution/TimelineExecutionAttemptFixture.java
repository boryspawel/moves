package com.motionecosystem.trainingexecution;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;

/** ORM fixture kept in the entity package because execution attempts intentionally have no public constructor. */
public final class TimelineExecutionAttemptFixture {
    private TimelineExecutionAttemptFixture() {
    }

    public static void completed(EntityManager entityManager, UUID participantId, UUID plannedSessionId,
                                 String idempotencyKey, Instant completedAt) {
        SessionExecutionAttempt attempt = new SessionExecutionAttempt(participantId, plannedSessionId, null,
                "STANDARD", idempotencyKey, completedAt.minusSeconds(600));
        attempt.complete(completedAt);
        entityManager.persist(attempt);
    }
}
