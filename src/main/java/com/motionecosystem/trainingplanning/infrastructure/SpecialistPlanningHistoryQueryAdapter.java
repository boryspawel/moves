package com.motionecosystem.trainingplanning.infrastructure;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.trainingplanning.api.SpecialistPlanningHistoryQueryPort;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
class SpecialistPlanningHistoryQueryAdapter implements SpecialistPlanningHistoryQueryPort {
    private final EntityManager entityManager;
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;

    @Override
    @Transactional(readOnly = true)
    public List<SessionSnapshot> sessionCandidates(String subject, UUID participantId, ActingContext context,
                                                   LocalDate fromInclusive, LocalDate toExclusive) {
        if (fromInclusive == null || toExclusive == null || !toExclusive.isAfter(fromInclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "history date range is required");
        }
        if (authorize(subject, participantId, context).isEmpty()) return List.of();
        return rows("""
                SELECT session, revision, plan FROM PlannedSessionJpaEntity session, MicrocycleJpaEntity microcycle,
                     TrainingCycleJpaEntity cycle, PlanRevisionJpaEntity revision, TrainingPlanJpaEntity plan
                WHERE session.participantId = :participantId AND session.scheduledDate >= :fromInclusive
                  AND session.scheduledDate < :toExclusive AND session.microcycleId = microcycle.id
                  AND microcycle.cycleId = cycle.id AND cycle.revisionId = revision.id AND revision.planId = plan.id
                ORDER BY session.scheduledDate, session.id
                """).setParameter("participantId", participantId).setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive).getResultList().stream().map(SpecialistPlanningHistoryQueryAdapter::snapshot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionSnapshot> sessionsByIds(String subject, UUID participantId, ActingContext context, Set<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty() || authorize(subject, participantId, context).isEmpty()) return List.of();
        return rows("""
                SELECT session, revision, plan FROM PlannedSessionJpaEntity session, MicrocycleJpaEntity microcycle,
                     TrainingCycleJpaEntity cycle, PlanRevisionJpaEntity revision, TrainingPlanJpaEntity plan
                WHERE session.participantId = :participantId AND session.id IN :sessionIds
                  AND session.microcycleId = microcycle.id AND microcycle.cycleId = cycle.id
                  AND cycle.revisionId = revision.id AND revision.planId = plan.id ORDER BY session.id
                """).setParameter("participantId", participantId).setParameter("sessionIds", sessionIds)
                .getResultList().stream().map(SpecialistPlanningHistoryQueryAdapter::snapshot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> visibleSessionIds(String subject, UUID participantId, ActingContext context, Set<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty() || authorize(subject, participantId, context).isEmpty()) return Set.of();
        return Set.copyOf(entityManager.createQuery("""
                SELECT session.id FROM PlannedSessionJpaEntity session
                WHERE session.participantId = :participantId AND session.id IN :sessionIds
                """, UUID.class).setParameter("participantId", participantId)
                .setParameter("sessionIds", sessionIds).getResultList());
    }

    private jakarta.persistence.TypedQuery<Object[]> rows(String query) {
        return entityManager.createQuery(query, Object[].class);
    }

    private Optional<UUID> authorize(String subject, UUID participantId, ActingContext context) {
        try {
            if (participantId == null || context == null || context.role() == null) return Optional.empty();
            var account = accounts.requireActive(subject);
            if (!account.hasProfile(ProfileType.SPECIALIST)) return Optional.empty();
            authorization.requireCapabilities(account.id(), participantId, context, Set.of(capability(context)), purpose(context));
            return Optional.of(account.id());
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) return Optional.empty();
            throw error;
        }
    }

    private static Capability capability(ActingContext context) {
        return context.role() == ProfessionalRole.TRAINER ? Capability.PLAN_PERFORMANCE : Capability.PLAN_FUNCTIONAL_RECOVERY;
    }

    private static Purpose purpose(ActingContext context) {
        return context.role() == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY;
    }

    private static SessionSnapshot snapshot(Object[] row) {
        PlannedSessionJpaEntity session = (PlannedSessionJpaEntity) row[0];
        PlanRevisionJpaEntity revision = (PlanRevisionJpaEntity) row[1];
        TrainingPlanJpaEntity plan = (TrainingPlanJpaEntity) row[2];
        return new SessionSnapshot(session.id, session.participantId, plan.id, revision.id, session.status.name(),
                session.kind.name(), session.title, session.scheduledDate, session.availableFrom, session.availableTo);
    }
}
