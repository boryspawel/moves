package com.motionecosystem.safety;

import com.motionecosystem.safety.api.SafetyAssessmentPort;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionSafetyDecisionService implements SessionSafetyDecisionQueryPort {
    private final SafetyAssessmentRepository assessments;
    private final SafetyAssessmentPort safety;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SessionSafetyDecision> evaluateForSessions(UUID participantAccountId, UUID planRevisionId,
                                                                  Collection<UUID> sessionIds, Instant evaluatedAt) {
        if (sessionIds == null || sessionIds.isEmpty()) return Map.of();
        var assessment = assessments.findFirstByRevisionIdOrderByAssessedAtDesc(planRevisionId)
                .flatMap(item -> safety.findAssessment(item.id, evaluatedAt))
                .filter(item -> participantAccountId.equals(item.participantAccountId()))
                .filter(item -> safety.isRestrictionSnapshotFresh(item.id(), participantAccountId, evaluatedAt));
        if (assessment.isEmpty()) return sessionIds.stream().collect(Collectors.toMap(Function.identity(), id ->
                new SessionSafetyDecision(id, SafetyDecisionStatus.NOT_ASSESSED, null, List.of(), null)));
        var snapshot = assessment.get();
        SafetyDecisionStatus status = switch (snapshot.effectiveResult()) {
            case PASS, INFO -> SafetyDecisionStatus.ALLOWED;
            case WARNING -> SafetyDecisionStatus.REQUIRES_REVIEW;
            case HARD_BLOCK -> SafetyDecisionStatus.BLOCKED;
        };
        List<String> reasons = snapshot.factors().stream().filter(factor -> !factor.activelyOverridden())
                .map(SafetyAssessmentPort.FactorSnapshot::explanationCode).distinct().toList();
        return sessionIds.stream().collect(Collectors.toMap(Function.identity(), id ->
                new SessionSafetyDecision(id, status, snapshot.id(), reasons, snapshot.assessedAt())));
    }
}
