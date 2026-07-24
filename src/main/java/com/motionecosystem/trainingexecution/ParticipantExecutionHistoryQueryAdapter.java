package com.motionecosystem.trainingexecution;

import com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ParticipantExecutionHistoryQueryAdapter implements ParticipantExecutionHistoryQueryPort {
    private final SessionExecutionAttemptRepository attempts;
    @Override @Transactional(readOnly = true)
    public List<ExecutionStart> starts(UUID participant, Instant from, Instant to, int limit) {
        return attempts.findByParticipantAccountIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(participant, from, to, PageRequest.of(0, limit)).stream()
          .map(item -> new ExecutionStart(item.id, item.plannedSessionId, item.planRevisionId, item.status,
                  item.selectedVariantType, item.startedAt, item.completedAt, item.abandonedAt, item.updatedAt,
                  item.abandonmentReason)).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ExecutionStart> timeline(UUID participant, Instant from, Instant to, SeekCursor after, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<SessionExecutionAttempt> result = after == null
                ? attempts.findTimelineInitial(participant, from, to, page)
                : attempts.findTimelineAfter(participant, from, to, after.effectiveFrom(), after.recordedAt(),
                        after.stableId(), page);
        return result.stream()
                .map(item -> new ExecutionStart(item.id, item.plannedSessionId, item.planRevisionId, item.status,
                        item.selectedVariantType, item.startedAt, item.completedAt, item.abandonedAt, item.updatedAt,
                        item.abandonmentReason)).toList();
    }
}
