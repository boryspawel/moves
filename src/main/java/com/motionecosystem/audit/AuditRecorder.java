package com.motionecosystem.audit;

import java.util.UUID;

public interface AuditRecorder {
    void record(String actorSubject, String eventType, String aggregateType, UUID aggregateId);
}
