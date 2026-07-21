package com.motionecosystem.audit;

import com.motionecosystem.audit.api.TransactionalOutbox;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A deliberately invoked local dispatcher; delivery is retried while published_at is null. */
@Service
@RequiredArgsConstructor
class JpaTransactionalOutbox implements TransactionalOutbox {

    private static final int BATCH_SIZE = 50;

    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public UUID append(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt) {
        OutboxEventJpaEntity event = OutboxEventJpaEntity.event(
                aggregateType, aggregateId, eventType, payload, occurredAt);
        entityManager.persist(event);
        return event.id;
    }

    @Override
    @Transactional
    public int dispatchPending(OutboxConsumer consumer) {
        List<OutboxEventJpaEntity> events = entityManager.createQuery("""
                SELECT event FROM OutboxEventJpaEntity event
                WHERE event.publishedAt IS NULL
                ORDER BY event.occurredAt, event.id
                """, OutboxEventJpaEntity.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(BATCH_SIZE)
                .getResultList();
        Instant publishedAt = clock.instant();
        for (OutboxEventJpaEntity event : events) {
            consumer.publish(new OutboxMessage(
                    event.id,
                    event.aggregateType,
                    event.aggregateId,
                    event.eventType,
                    event.payload,
                    event.occurredAt));
            event.publicationAttempts++;
            event.publishedAt = publishedAt;
        }
        return events.size();
    }

}

@Entity(name = "OutboxEventJpaEntity")
@Table(name = "outbox_event", schema = "audit")
class OutboxEventJpaEntity {
    @Id UUID id;
    @Column(name = "aggregate_type") String aggregateType;
    @Column(name = "aggregate_id") UUID aggregateId;
    @Column(name = "event_type") String eventType;
    String payload;
    @Column(name = "occurred_at") Instant occurredAt;
    @Column(name = "published_at") Instant publishedAt;
    @Column(name = "publication_attempts") int publicationAttempts;

    protected OutboxEventJpaEntity() {
    }

    static OutboxEventJpaEntity event(
            String aggregateType, UUID aggregateId, String eventType, String payload, Instant now) {
        OutboxEventJpaEntity item = new OutboxEventJpaEntity();
        item.id = UUID.randomUUID();
        item.aggregateType = aggregateType;
        item.aggregateId = aggregateId;
        item.eventType = eventType;
        item.payload = payload;
        item.occurredAt = now;
        return item;
    }
}
