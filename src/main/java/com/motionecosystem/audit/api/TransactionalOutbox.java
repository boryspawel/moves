package com.motionecosystem.audit.api;

import java.time.Instant;
import java.util.UUID;

public interface TransactionalOutbox {

    UUID append(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt);

    int dispatchPending(OutboxConsumer consumer);

    @FunctionalInterface
    interface OutboxConsumer {
        void publish(OutboxMessage message);
    }

    record OutboxMessage(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt) {
    }
}
