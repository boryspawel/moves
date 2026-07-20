package com.motionecosystem.audit;

import java.time.Clock;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaAuditRecorder implements AuditRecorder {

    private final AuditEventRepository events;
    private final Clock clock;

    @Override
    public void record(String actorSubject, String eventType, String aggregateType, UUID aggregateId) {
        events.save(new AuditEvent(actorSubject, eventType, aggregateType, aggregateId, clock.instant()));
    }
}
