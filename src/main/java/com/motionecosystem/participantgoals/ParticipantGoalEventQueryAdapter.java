package com.motionecosystem.participantgoals;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.participantgoals.api.ParticipantGoalEventQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Actor-scoped goal event read model: a plan grant never releases observation values. */
@Component
class ParticipantGoalEventQueryAdapter implements ParticipantGoalEventQueryPort {
    private final ParticipantGoalEventRepository events;
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;

    ParticipantGoalEventQueryAdapter(ParticipantGoalEventRepository events, CurrentAccountService accounts,
                                     SpecialistAuthorizationPort authorization) {
        this.events = events;
        this.accounts = accounts;
        this.authorization = authorization;
    }

    @Override
    public List<ParticipantGoalEventSummary> timeline(String subject, UUID participantId, ActingContext context,
                                                       Instant from, Instant to, SeekCursor after, int limit) {
        if (participantId == null || from == null || to == null || limit < 1) return List.of();
        Optional<UUID> actor = specialistForPlan(subject, participantId, context);
        if (actor.isEmpty()) return List.of();
        boolean measurementsVisible = canReadMeasurements(actor.get(), participantId, context);
        var page = PageRequest.of(0, limit);
        var eventsPage = after == null
                ? events.timelineWithoutCursor(actor.get(), participantId, from, to, page)
                : events.timelineAfterCursor(actor.get(), participantId, from, to, after.effectiveAt(), after.recordedAt(), after.eventId(), page);
        return eventsPage.stream().filter(item -> measurementsVisible || item.observationId == null)
                .map(this::summary).toList();
    }

    @Override
    public Optional<ParticipantGoalEventSummary> findByParticipantId(String subject, UUID participantId,
                                                                      ActingContext context, UUID eventId) {
        if (participantId == null || eventId == null) return Optional.empty();
        Optional<UUID> actor = specialistForPlan(subject, participantId, context);
        if (actor.isEmpty()) return Optional.empty();
        boolean measurementsVisible = canReadMeasurements(actor.get(), participantId, context);
        return events.findOwnedEvent(actor.get(), participantId, eventId)
                .filter(item -> measurementsVisible || item.observationId == null).map(this::summary);
    }

    private Optional<UUID> specialistForPlan(String subject, UUID participant, ActingContext context) {
        if (context == null || context.role() == null) return Optional.empty();
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) return Optional.empty();
        try {
            authorization.requireCapabilities(account.id(), participant, context,
                    Set.of(context.role() == ProfessionalRole.TRAINER ? Capability.PLAN_PERFORMANCE : Capability.PLAN_FUNCTIONAL_RECOVERY),
                    purpose(context));
            return Optional.of(account.id());
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode() == HttpStatus.FORBIDDEN) return Optional.empty();
            throw denied;
        }
    }

    private boolean canReadMeasurements(UUID actor, UUID participant, ActingContext context) {
        try {
            authorization.requireCapabilities(actor, participant, context, Set.of(Capability.VIEW_MEASUREMENTS), purpose(context));
            return true;
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode() == HttpStatus.FORBIDDEN) return false;
            throw denied;
        }
    }

    private static Purpose purpose(ActingContext context) {
        return context.role() == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY;
    }

    private ParticipantGoalEventSummary summary(ParticipantGoalEvent item) {
        return new ParticipantGoalEventSummary(item.id, item.goalId, item.participantId, item.observationId, item.eventType.name(),
                item.fromStatus == null ? null : item.fromStatus.name(), item.toStatus.name(), item.effectiveAt, item.recordedAt,
                item.category, item.title, item.description, item.priority, item.targetDate, item.metricCode,
                item.observationValue, item.observationUnit, item.measuredAt, item.progressState);
    }
}
