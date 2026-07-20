package com.motionecosystem.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditRecorder implements AuditRecorder {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcAuditRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void record(String actorSubject, String eventType, String aggregateType, UUID aggregateId) {
        jdbc.update("""
                INSERT INTO audit.audit_event
                    (id, actor_subject, event_type, aggregate_type, aggregate_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), actorSubject, eventType, aggregateType, aggregateId, Timestamp.from(clock.instant()));
    }
}
