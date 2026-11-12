package com.motionecosystem.trainingexecution.infrastructure;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.trainingexecution.api.SpecialistExecutionHistoryQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
@RequiredArgsConstructor
class SpecialistExecutionHistoryQueryAdapter implements SpecialistExecutionHistoryQueryPort {
    private final EntityManager entityManager;
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionFact> executionCandidates(String subject, UUID participantId, ActingContext context,
                                                    Instant fromInclusive, Instant toExclusive) {
        requireRange(fromInclusive, toExclusive);
        if (authorize(subject, participantId, context).isEmpty()) return List.of();
        return rows("SELECT execution.id, execution.participantAccountId, execution.plannedSessionId, execution.attemptId, "
                + "execution.recordingSource, execution.recorderAccountId, execution.outcome, execution.recordedAt, "
                + "execution.declaredCompletion, COUNT(result.id) "
                + "FROM SessionExecutionJpaEntity execution LEFT JOIN ExerciseResultJpaEntity result "
                + "ON result.sessionExecutionId = execution.id "
                + "WHERE execution.participantAccountId = :participantId "
                + "AND execution.recordedAt >= :fromInclusive AND execution.recordedAt < :toExclusive "
                + "GROUP BY execution.id, execution.participantAccountId, execution.plannedSessionId, execution.attemptId, "
                + "execution.recordingSource, execution.recorderAccountId, execution.outcome, execution.recordedAt, "
                + "execution.declaredCompletion ORDER BY execution.recordedAt, execution.id")
                .setParameter("participantId", participantId).setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive).getResultList().stream().map(SpecialistExecutionHistoryQueryAdapter::fact).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionFact> executionsByPlannedSessionIds(String subject, UUID participantId, ActingContext context,
                                                               Set<UUID> plannedSessionIds) {
        if (plannedSessionIds == null || plannedSessionIds.isEmpty() || authorize(subject, participantId, context).isEmpty()) return List.of();
        return rows("SELECT execution.id, execution.participantAccountId, execution.plannedSessionId, execution.attemptId, "
                + "execution.recordingSource, execution.recorderAccountId, execution.outcome, execution.recordedAt, "
                + "execution.declaredCompletion, COUNT(result.id) "
                + "FROM SessionExecutionJpaEntity execution LEFT JOIN ExerciseResultJpaEntity result "
                + "ON result.sessionExecutionId = execution.id "
                + "WHERE execution.participantAccountId = :participantId AND execution.plannedSessionId IN :plannedSessionIds "
                + "GROUP BY execution.id, execution.participantAccountId, execution.plannedSessionId, execution.attemptId, "
                + "execution.recordingSource, execution.recorderAccountId, execution.outcome, execution.recordedAt, "
                + "execution.declaredCompletion ORDER BY execution.recordedAt, execution.id")
                .setParameter("participantId", participantId).setParameter("plannedSessionIds", plannedSessionIds)
                .getResultList().stream().map(SpecialistExecutionHistoryQueryAdapter::fact).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionFact> executionsByIds(String subject, UUID participantId, ActingContext context, Set<UUID> executionIds) {
        if (executionIds == null || executionIds.isEmpty() || authorize(subject, participantId, context).isEmpty()) return List.of();
        return rows("SELECT execution.id, execution.participantAccountId, execution.plannedSessionId, execution.attemptId, "
                + "execution.recordingSource, execution.recorderAccountId, execution.outcome, execution.recordedAt, "
                + "execution.declaredCompletion, COUNT(result.id) "
                + "FROM SessionExecutionJpaEntity execution LEFT JOIN ExerciseResultJpaEntity result ON result.sessionExecutionId = execution.id "
                + "WHERE execution.participantAccountId = :participantId AND execution.id IN :executionIds "
                + "GROUP BY execution.id, execution.participantAccountId, execution.plannedSessionId, execution.attemptId, "
                + "execution.recordingSource, execution.recorderAccountId, execution.outcome, execution.recordedAt, execution.declaredCompletion")
                .setParameter("participantId", participantId).setParameter("executionIds", executionIds)
                .getResultList().stream().map(SpecialistExecutionHistoryQueryAdapter::fact).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionLink> executionLinksByPlannedSessionIds(String subject, UUID participantId, ActingContext context,
                                                                  Set<UUID> plannedSessionIds) {
        if (plannedSessionIds == null || plannedSessionIds.isEmpty() || authorize(subject, participantId, context).isEmpty()) return List.of();
        return entityManager.createQuery("SELECT execution.id, execution.participantAccountId, execution.plannedSessionId, "
                + "execution.recorderAccountId, execution.recordingSource FROM SessionExecutionJpaEntity execution "
                + "WHERE execution.participantAccountId = :participantId AND execution.plannedSessionId IN :plannedSessionIds", Object[].class)
                .setParameter("participantId", participantId).setParameter("plannedSessionIds", plannedSessionIds)
                .getResultList().stream().map(row -> new ExecutionLink((UUID) row[0], (UUID) row[1], (UUID) row[2],
                        (UUID) row[3], (String) row[4])).toList();
    }

    private TypedQuery<Object[]> rows(String query) {
        return entityManager.createQuery(query, Object[].class);
    }

    private Optional<UUID> authorize(String subject, UUID participantId, ActingContext context) {
        try {
            if (participantId == null || context == null || context.role() == null) return Optional.empty();
            var account = accounts.requireActive(subject);
            if (!account.hasProfile(ProfileType.SPECIALIST)) return Optional.empty();
            authorization.requireCapabilities(account.id(), participantId, context, Set.of(Capability.VIEW_ADHERENCE_WORKLIST), purpose(context));
            return Optional.of(account.id());
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) return Optional.empty();
            throw error;
        }
    }

    private static Purpose purpose(ActingContext context) {
        return context.role() == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY;
    }

    private static void requireRange(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !toExclusive.isAfter(fromInclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "history range is required");
        }
    }

    private static ExecutionFact fact(Object[] row) {
        return new ExecutionFact((UUID) row[0], (UUID) row[1], (UUID) row[2], (UUID) row[3], (String) row[4],
                (UUID) row[5], (String) row[6], (Instant) row[7], (Boolean) row[8], ((Number) row[9]).longValue());
    }
}
