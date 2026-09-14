package com.motionecosystem.trainingexecution;

import com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ParticipantExecutionHistoryQueryAdapter implements ParticipantExecutionHistoryQueryPort {
    private final SessionExecutionAttemptRepository attempts;
    private final SessionExecutionPersistence executions;
    private final SessionExecutionAttemptFactRepository facts;
    private final PlanRevisionQueryPort revisions;
    private final ObjectMapper objectMapper;
    @Override @Transactional(readOnly = true)
    public List<ExecutionStart> starts(UUID participant, Instant from, Instant to, int limit) {
        return attempts.findByParticipantAccountIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(participant, from, to, PageRequest.of(0, limit)).stream()
          .map(this::view).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ExecutionStart> timeline(UUID participant, Instant from, Instant to, SeekCursor after, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<SessionExecutionAttempt> result = after == null
                ? attempts.findTimelineInitial(participant, from, to, page)
                : attempts.findTimelineAfter(participant, from, to, after.effectiveFrom(), after.recordedAt(),
                        after.stableId(), page);
        return result.stream()
                .map(this::view).toList();
    }
    private ExecutionStart view(SessionExecutionAttempt attempt) {
        var execution = executions.findByAttemptId(attempt.id).orElse(null);
        var current = facts.findByAttemptIdOrderByExercisePrescriptionIdAscRevisionNumberDesc(attempt.id).stream()
                .collect(java.util.stream.Collectors.toMap(item -> item.exercisePrescriptionId, item -> item,
                        (left, right) -> left.revisionNumber > right.revisionNumber ? left : right));
        int performed = (int) current.values().stream().filter(item -> "PERFORMED".equals(item.outcome)).count();
        int partial = (int) current.values().stream().filter(item -> "PARTIAL".equals(item.outcome)).count();
        int skipped = (int) current.values().stream().filter(item -> "SKIPPED".equals(item.outcome)).count();
        Integer notReached = selectedPrescriptionCount(attempt).map(count -> Math.max(0, count - current.size())).orElse(null);
        return new ExecutionStart(attempt.id, attempt.plannedSessionId, attempt.planRevisionId, attempt.status,
                attempt.selectedVariantType, attempt.startedAt, attempt.completedAt, attempt.abandonedAt, attempt.updatedAt,
                attempt.abandonmentReason, execution == null ? null : execution.execution().outcome(), performed, partial, skipped,
                notReached,
                execution == null ? null : execution.report().painLevel(), execution == null ? null : execution.report().difficultyLevel(),
                execution == null ? null : execution.execution().stopReason());
    }
    private java.util.Optional<Integer> selectedPrescriptionCount(SessionExecutionAttempt attempt) {
        if (attempt.planRevisionId == null) return java.util.Optional.empty();
        return revisions.findRevision(attempt.planRevisionId).flatMap(revision -> revision.cycles().stream()
                .flatMap(cycle -> cycle.microcycles().stream()).flatMap(microcycle -> microcycle.sessions().stream())
                .filter(session -> session.id().equals(attempt.plannedSessionId)).findFirst()).map(session -> {
            if ("STANDARD".equals(attempt.selectedVariantType)) return session.prescriptions().size();
            return session.variants().stream().filter(variant -> attempt.selectedVariantType.equals(variant.type()))
                    .findFirst().map(variant -> variant.items().size()).orElse(null);
        });
    }
}
