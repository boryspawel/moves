package com.motionecosystem.safety.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SessionSafetyDecisionQueryPort {
    Map<UUID, SessionSafetyDecision> evaluateForSessions(UUID participantAccountId, UUID planRevisionId,
                                                          Collection<UUID> plannedSessionIds, Instant evaluatedAt);
    enum SafetyDecisionStatus { NOT_ASSESSED, ALLOWED, REQUIRES_REVIEW, BLOCKED }
    record SessionSafetyDecision(UUID plannedSessionId, SafetyDecisionStatus status, UUID assessmentId,
                                 List<String> reasonCodes, Instant assessedAt) { }
}
