package com.motionecosystem.specialist;

import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import com.motionecosystem.participant.api.ParticipantSummaryQueryPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Specialist-owned composition that consumes only public read ports of data owners. */
@Service
@RequiredArgsConstructor
public class SpecialistParticipantReadService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private final CurrentAccountService accounts;
    private final SpecialistProfileService profiles;
    private final SpecialistAuthorizationPort authorization;
    private final ParticipantSummaryQueryPort participants;
    private final ParticipantContextQueryPort participantContexts;
    private final SpecialistAppointmentQueryPort appointments;
    private final PlanRevisionQueryPort revisions;
    private final ParticipantExecutionHistoryQueryPort executionHistory;
    private final ParticipantSpecialistRelationshipRepository relationships;
    private final SpecialistWorklistService worklist;
    private final AuditRecorder audit;
    private final Clock clock;

    public SpecialistParticipantWorkspaceView workspace(String subject, UUID participantId) {
        Access access = authorize(subject, participantId);
        audit.record(subject, "SPECIALIST_PARTICIPANT_WORKSPACE_VIEWED", "ParticipantAccount", participantId);
        ParticipantHeader participant = participant(participantId);
        Optional<PlanRevisionQueryPort.PlanRevisionSnapshot> revision = revisions.findActiveRevision(participantId);
        Instant now = clock.instant();
        List<SpecialistAppointmentQueryPort.AppointmentSummary> upcoming = appointments.findForParticipant(
                access.specialistId(), participantId, now, now.plusSeconds(366L * 24 * 60 * 60), MAX_LIMIT).stream()
                .filter(item -> "SCHEDULED".equals(item.status()))
                .sorted(Comparator.comparing(SpecialistAppointmentQueryPort.AppointmentSummary::startsAt))
                .limit(1)
                .toList();
        List<ParticipantExecutionHistoryQueryPort.ExecutionStart> recentExecutions = canViewExecutionHistory(access, participantId)
                ? executionHistory.starts(participantId, now.minusSeconds(366L * 24 * 60 * 60), now.plusSeconds(1), DEFAULT_LIMIT)
                : List.of();
        List<AttentionItemView> attention = attention(subject, participantId, access);
        return new SpecialistParticipantWorkspaceView(now, participant,
                relationship(access.specialistId(), participantId), capabilities(access.decision()),
                upcoming.isEmpty() ? null : appointment(upcoming.getFirst()),
                revision.map(value -> activePlan(value, now, recentExecutions)).orElse(null),
                revision.map(value -> goals(value.goals())).orElseGet(List::of), adherence(), recentProgress(recentExecutions),
                activeProblems(attention), attention, quickActions(access.decision(), upcoming, revision, attention));
    }

    public ParticipantTimelineView timeline(String subject, UUID participantId, TimelineQuery query) {
        Access access = authorize(subject, participantId);
        audit.record(subject, "SPECIALIST_PARTICIPANT_TIMELINE_VIEWED", "ParticipantAccount", participantId);
        TimelineQuery normalized = normalize(query, participantId);
        Cursor cursor = parseCursor(normalized.cursor());
        List<ParticipantTimelineEvent> events = new ArrayList<>();
        if (normalized.types().contains(TimelineType.APPOINTMENT)) {
            appointments.timeline(access.specialistId(), participantId, normalized.from(), normalized.to(),
                            cursor == null ? null : new SpecialistAppointmentQueryPort.SeekCursor(
                                    cursor.effectiveFrom(), cursor.recordedAt(), cursor.eventId()), normalized.limit() + 1)
                    .forEach(item -> events.add(appointmentEvent(item)));
        }
        Optional<PlanRevisionQueryPort.PlanRevisionSnapshot> revision = revisions.findActiveRevision(participantId);
        revision.ifPresent(value -> addPlanEvents(events, value, normalized));
        if (normalized.types().contains(TimelineType.EXECUTION) && canViewExecutionHistory(access, participantId)) {
            executionHistory.timeline(participantId, normalized.from(), normalized.to(), null, MAX_LIMIT)
                    .forEach(item -> events.add(executionEvent(item, revision.orElse(null))));
        }
        List<ParticipantTimelineEvent> aggregatedEvents = aggregate(events, normalized.granularity());
        Comparator<ParticipantTimelineEvent> order = Comparator
                .comparing(ParticipantTimelineEvent::effectiveFrom).reversed()
                .thenComparing(ParticipantTimelineEvent::recordedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ParticipantTimelineEvent::eventId);
        List<ParticipantTimelineEvent> page = aggregatedEvents.stream().sorted(order)
                .filter(event -> afterCursor(event, cursor))
                .limit(normalized.limit() + 1L).toList();
        boolean hasMore = page.size() > normalized.limit();
        List<ParticipantTimelineEvent> items = hasMore ? page.subList(0, normalized.limit()) : page;
        String nextCursor = hasMore ? cursor(items.getLast()) : null;
        return new ParticipantTimelineView(clock.instant(), normalized.from(), normalized.to(), normalized.granularity(),
                items, nextCursor);
    }

    private Access authorize(String subject, UUID participantId) {
        if (participantId == null) throw bad("participantId is required");
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required");
        SpecialistProfileService.ProfileView profile = profiles.find(account.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "specialist profile is required"));
        ProfessionalRole role = ProfessionalRole.valueOf(profile.specialistKind().name());
        Capability capability = role == ProfessionalRole.TRAINER ? Capability.PLAN_PERFORMANCE : Capability.PLAN_FUNCTIONAL_RECOVERY;
        Purpose purpose = role == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY;
        return new Access(account.id(), authorization.requireCapabilities(account.id(), participantId,
                new ActingContext(role), Set.of(capability), purpose));
    }

    private boolean canViewExecutionHistory(Access access, UUID participantId) {
        try {
            authorization.requireCapabilities(access.specialistId(), participantId,
                    new ActingContext(access.decision().actingRole()), Set.of(Capability.VIEW_ADHERENCE_WORKLIST),
                    access.decision().purpose());
            return true;
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode() == HttpStatus.FORBIDDEN) return false;
            throw denied;
        }
    }

    private ParticipantHeader participant(UUID participantId) {
        ParticipantSummaryQueryPort.ParticipantSummary summary = participants.findSummary(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant profile not found"));
        String timeZone = participantContexts.findContext(participantId).map(value -> value.timeZone().getId()).orElse(null);
        return new ParticipantHeader(summary.participantAccountId(), summary.displayName(), null, null, timeZone,
                List.of("OPEN_WORKSPACE", "OPEN_TIMELINE"));
    }

    private RelationshipView relationship(UUID specialistId, UUID participantId) {
        ParticipantSpecialistRelationship relationship = relationships
                .findBySpecialistAccountIdAndParticipantAccountId(specialistId, participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "active participant-specialist relationship is required"));
        return new RelationshipView(relationship.status().name(), relationship.activatedAt());
    }

    private List<AttentionItemView> attention(String subject, UUID participantId, Access access) {
        try {
            return worklist.forParticipant(subject, participantId, new ActingContext(access.decision().actingRole()),
                            access.decision().purpose()).stream()
                    .limit(10)
                    .map(item -> new AttentionItemView(item.id(), item.category(), item.priority(), item.status(), item.minimalData(),
                            item.createdAt(), item.snoozedUntil(), List.of("OPEN_WORKLIST_ITEM", "ACKNOWLEDGE", "RESOLVE")))
                    .toList();
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode() == HttpStatus.FORBIDDEN) return List.of();
            throw denied;
        }
    }

    private TimelineQuery normalize(TimelineQuery query, UUID participantId) {
        TimelineQuery requested = query == null ? new TimelineQuery(null, null, null, null, null, null) : query;
        Granularity granularity = requested.granularity() == null ? Granularity.DETAIL : requested.granularity();
        ZoneId zone = participantContexts.findContext(participantId).map(ParticipantContextQueryPort.ParticipantContext::timeZone)
                .orElse(ZoneId.of("UTC"));
        Instant defaultTo = clock.instant();
        Instant defaultFrom = switch (granularity) {
            case DETAIL -> defaultTo.minusSeconds(14L * 24 * 60 * 60);
            case WEEK -> defaultTo.minusSeconds(93L * 24 * 60 * 60);
            case MONTH -> LocalDate.now(clock.withZone(zone)).minusMonths(12).atStartOfDay(zone).toInstant();
        };
        Instant from = requested.from() == null ? defaultFrom : requested.from();
        Instant to = requested.to() == null ? defaultTo : requested.to();
        if (!to.isAfter(from)) throw bad("to must be after from");
        long maximumSeconds = switch (granularity) {
            case DETAIL -> 14L * 24 * 60 * 60;
            case WEEK -> 93L * 24 * 60 * 60;
            case MONTH -> 366L * 24 * 60 * 60;
        };
        if (to.getEpochSecond() - from.getEpochSecond() > maximumSeconds) throw bad("requested range exceeds the granularity limit");
        int limit = requested.limit() == null ? DEFAULT_LIMIT : requested.limit();
        if (limit < 1 || limit > MAX_LIMIT) throw bad("limit must be between 1 and " + MAX_LIMIT);
        Set<TimelineType> types = requested.types() == null || requested.types().isEmpty()
                ? EnumSet.allOf(TimelineType.class) : EnumSet.copyOf(requested.types());
        parseCursor(requested.cursor());
        return new TimelineQuery(from, to, types, granularity, requested.cursor(), limit);
    }

    private void addPlanEvents(List<ParticipantTimelineEvent> events, PlanRevisionQueryPort.PlanRevisionSnapshot revision,
                               TimelineQuery query) {
        if (!query.types().contains(TimelineType.SESSION)) return;
        revision.cycles().stream().flatMap(cycle -> cycle.microcycles().stream()).flatMap(microcycle -> microcycle.sessions().stream())
                .filter(session -> session.scheduledDate() != null)
                .map(session -> new SessionOccurrence(session, session.scheduledDate().atStartOfDay(ZoneId.of("UTC")).toInstant()))
                .filter(item -> !item.effectiveAt().isBefore(query.from()) && item.effectiveAt().isBefore(query.to()))
                .forEach(item -> events.add(new ParticipantTimelineEvent("planned-session:" + item.session().id(), "SESSION_PLANNED", "SESSION",
                        item.session().status(), item.effectiveAt(), null, revision.createdAt(), null, item.session().title(),
                        "Planned session", "NORMAL", "OPERATIONAL", revision.authorAccountId(), "PLAN_REVISION", List.of(),
                        revision.revisionId(), null, null, null, List.of("OPEN_ACTIVE_PLAN"),
                        new EventDetail("PLANNED_SESSION", item.session().id().toString()))));
    }

    private static ParticipantTimelineEvent appointmentEvent(SpecialistAppointmentQueryPort.AppointmentSummary item) {
        String type = switch (item.status()) {
            case "COMPLETED" -> "APPOINTMENT_COMPLETED";
            case "CANCELLED" -> "APPOINTMENT_CANCELLED";
            case "NO_SHOW" -> "APPOINTMENT_NO_SHOW";
            default -> "APPOINTMENT_SCHEDULED";
        };
        return new ParticipantTimelineEvent("appointment:" + item.appointmentId(), type, "APPOINTMENT", item.status(),
                item.startsAt(), item.endsAt(), item.recordedAt(), item.updatedAt(), item.type(), item.shortPurpose(), "NORMAL", "OPERATIONAL",
                null, "CALENDAR_APPOINTMENT", List.of(), null, null, null, null, List.of("OPEN_APPOINTMENT"),
                new EventDetail("APPOINTMENT", item.appointmentId().toString()));
    }

    private static ParticipantTimelineEvent executionEvent(ParticipantExecutionHistoryQueryPort.ExecutionStart item,
                                                            PlanRevisionQueryPort.PlanRevisionSnapshot activeRevision) {
        Instant effective = item.completedAt() != null ? item.completedAt() : item.abandonedAt() != null ? item.abandonedAt() : item.startedAt();
        String type = item.completedAt() != null ? "SESSION_COMPLETED" : item.abandonedAt() != null ? "SESSION_ABANDONED" : "SESSION_STARTED";
        return new ParticipantTimelineEvent("session-execution:" + item.attemptId(), type, "EXECUTION", item.state(),
                effective, null, item.startedAt(), item.updatedAt(), "Session execution", item.variant(), "NORMAL", "OPERATIONAL",
                null, "SESSION_EXECUTION_ATTEMPT", List.of(), item.planRevisionId(), comparison(item, activeRevision), null, null,
                List.of(), new EventDetail("SESSION_EXECUTION_ATTEMPT", item.attemptId().toString()));
    }

    private static List<ParticipantTimelineEvent> aggregate(List<ParticipantTimelineEvent> events, Granularity granularity) {
        if (granularity == Granularity.DETAIL) return events;
        Map<String, List<ParticipantTimelineEvent>> repeated = events.stream()
                .filter(item -> "SESSION".equals(item.category()) || "EXECUTION".equals(item.category()))
                .collect(java.util.stream.Collectors.groupingBy(item -> aggregateKey(item.effectiveFrom(), granularity)));
        List<ParticipantTimelineEvent> result = new ArrayList<>(events.stream()
                .filter(item -> !"SESSION".equals(item.category()) && !"EXECUTION".equals(item.category())).toList());
        repeated.forEach((key, source) -> {
            Instant from = source.stream().map(ParticipantTimelineEvent::effectiveFrom).min(Instant::compareTo).orElseThrow();
            Instant to = source.stream().map(ParticipantTimelineEvent::effectiveFrom).max(Instant::compareTo).orElseThrow();
            Instant recorded = source.stream().map(ParticipantTimelineEvent::recordedAt).filter(java.util.Objects::nonNull)
                    .max(Instant::compareTo).orElse(null);
            result.add(new ParticipantTimelineEvent("aggregate:" + granularity + ":" + key, granularity + "_SUMMARY", "SESSION",
                    "SUMMARY", to, from.equals(to) ? null : to, recorded, null, granularity == Granularity.WEEK ? "Weekly summary" : "Monthly summary",
                    source.size() + " source events", "NORMAL", "OPERATIONAL", null, "TIMELINE_AGGREGATE", List.of(), null,
                    null, null, null, List.of(), new EventDetail("TIMELINE_AGGREGATE", key, source.size(), from, to)));
        });
        return result;
    }

    private static String aggregateKey(Instant instant, Granularity granularity) {
        LocalDate date = instant.atZone(ZoneId.of("UTC")).toLocalDate();
        return granularity == Granularity.MONTH ? date.getYear() + "-" + String.format("%02d", date.getMonthValue())
                : date.get(java.time.temporal.WeekFields.ISO.weekBasedYear()) + "-W" + String.format("%02d",
                        date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
    }

    private static PlannedExecutionComparison comparison(ParticipantExecutionHistoryQueryPort.ExecutionStart execution,
                                                          PlanRevisionQueryPort.PlanRevisionSnapshot activeRevision) {
        if (activeRevision == null || !activeRevision.revisionId().equals(execution.planRevisionId())) {
            return new PlannedExecutionComparison(null, new PerformedSession(execution.plannedSessionId(), execution.variant(), null),
                    execution.state(), List.of());
        }
        PlanRevisionQueryPort.SessionSnapshot planned = activeRevision.cycles().stream()
                .flatMap(cycle -> cycle.microcycles().stream()).flatMap(microcycle -> microcycle.sessions().stream())
                .filter(session -> session.id().equals(execution.plannedSessionId())).findFirst().orElse(null);
        if (planned == null) return new PlannedExecutionComparison(null,
                new PerformedSession(execution.plannedSessionId(), execution.variant(), null), execution.state(), List.of());
        List<PlannedDose> doses = planned.prescriptions().stream().map(item -> new PlannedDose(item.id(), item.exerciseVersionId(),
                item.sets(), item.repetitions(), item.durationSeconds(), item.externalLoadValue(), item.externalLoadUnit(),
                item.distanceMeters(), item.tempo(), item.restSeconds())).toList();
        List<ExecutionDeviation> deviations = "STANDARD".equals(execution.variant()) ? List.of()
                : List.of(new ExecutionDeviation("VARIANT_SELECTED", null, execution.variant()));
        return new PlannedExecutionComparison(new PlannedSession(planned.id(), planned.title(), doses),
                new PerformedSession(execution.plannedSessionId(), execution.variant(), null), execution.state(), deviations);
    }

    private static AppointmentView appointment(SpecialistAppointmentQueryPort.AppointmentSummary item) {
        return new AppointmentView(item.appointmentId(), item.startsAt(), item.endsAt(), item.type(), item.status(), item.shortPurpose());
    }
    private static ActivePlanView activePlan(PlanRevisionQueryPort.PlanRevisionSnapshot value, Instant now,
                                             List<ParticipantExecutionHistoryQueryPort.ExecutionStart> executions) {
        int sessions = value.cycles().stream().flatMap(cycle -> cycle.microcycles().stream()).mapToInt(item -> item.sessions().size()).sum();
        PlanRevisionQueryPort.SessionSnapshot next = value.cycles().stream().flatMap(cycle -> cycle.microcycles().stream())
                .flatMap(microcycle -> microcycle.sessions().stream()).filter(session -> session.scheduledDate() != null)
                .filter(session -> !session.scheduledDate().atStartOfDay(ZoneId.of("UTC")).toInstant().isBefore(now))
                .min(Comparator.comparing(PlanRevisionQueryPort.SessionSnapshot::scheduledDate)).orElse(null);
        ParticipantExecutionHistoryQueryPort.ExecutionStart last = executions.stream().filter(item -> item.completedAt() != null)
                .max(Comparator.comparing(ParticipantExecutionHistoryQueryPort.ExecutionStart::completedAt)).orElse(null);
        return new ActivePlanView(value.planId(), value.revisionId(), null, value.status(), null, value.validFrom(), value.validTo(), sessions,
                next == null ? null : new SessionFactView(next.id(), next.title(), next.scheduledDate().atStartOfDay(ZoneId.of("UTC")).toInstant()),
                last == null ? null : new ExecutionFactView(last.attemptId(), last.completedAt(), last.state()),
                List.of("OPEN_ACTIVE_PLAN"));
    }
    private static List<GoalView> goals(List<PlanRevisionQueryPort.GoalSnapshot> goals) {
        return goals.stream().map(goal -> new GoalView(goal.id(), goal.title(), goal.category(), goal.status(), goal.targetDate(),
                goal.outcomes().stream().map(PlanRevisionQueryPort.GoalOutcomeSnapshot::baseline).filter(value -> value != null).findFirst().orElse(null),
                goal.outcomes().stream().map(PlanRevisionQueryPort.GoalOutcomeSnapshot::target).filter(value -> value != null).findFirst().orElse(null),
                null, goal.outcomes().stream().map(PlanRevisionQueryPort.GoalOutcomeSnapshot::unit).filter(value -> value != null).findFirst().orElse(null),
                null, "NO_DATA", List.of("OPEN_GOAL"))).toList();
    }
    private static AdherenceSummaryView adherence() { return new AdherenceSummaryView("NO_DATA", null, null, null, null, null, null, null, null); }
    private static RecentProgressView recentProgress(List<ParticipantExecutionHistoryQueryPort.ExecutionStart> executions) {
        return executions.isEmpty() ? new RecentProgressView("NO_DATA", null, null) : new RecentProgressView("AVAILABLE",
                executions.getFirst().completedAt() != null ? executions.getFirst().completedAt() : executions.getFirst().startedAt(), executions.getFirst().state());
    }
    private static List<ActiveProblemView> activeProblems(List<AttentionItemView> attention) {
        return attention.stream().map(item -> new ActiveProblemView(item.attentionId(), item.type(), item.priority(), item.status(),
                item.shortDescription(), item.createdAt(), item.createdAt(), "WORKLIST", item.availableActions())).toList();
    }
    private static List<String> quickActions(SpecialistAuthorizationPort.AuthorizationDecision decision,
                                             List<SpecialistAppointmentQueryPort.AppointmentSummary> appointments,
                                             Optional<PlanRevisionQueryPort.PlanRevisionSnapshot> revision,
                                             List<AttentionItemView> attention) {
        List<String> actions = new ArrayList<>(List.of("OPEN_TIMELINE", "SCHEDULE_APPOINTMENT"));
        if (!appointments.isEmpty()) actions.add("OPEN_NEXT_APPOINTMENT");
        if (revision.isPresent()) actions.add("OPEN_ACTIVE_PLAN");
        if (!attention.isEmpty() && decision.grantedCapabilities().contains(Capability.VIEW_ADHERENCE_WORKLIST)) actions.add("OPEN_ATTENTION_ITEMS");
        return List.copyOf(actions);
    }
    private static List<String> capabilities(SpecialistAuthorizationPort.AuthorizationDecision decision) {
        return decision.grantedCapabilities().stream().map(Enum::name).sorted().toList();
    }
    private static boolean afterCursor(ParticipantTimelineEvent event, Cursor cursor) {
        if (cursor == null) return true;
        int effective = event.effectiveFrom().compareTo(cursor.effectiveFrom());
        if (effective != 0) return effective < 0;
        int recorded = compareNullableDescending(event.recordedAt(), cursor.recordedAt());
        if (recorded != 0) return recorded > 0;
        return event.eventId().compareTo(cursor.eventId()) > 0;
    }
    private static int compareNullableDescending(Instant left, Instant right) {
        if (left == null) return right == null ? 0 : 1;
        if (right == null) return -1;
        return right.compareTo(left);
    }
    private static String cursor(ParticipantTimelineEvent item) {
        String recordedAt = item.recordedAt() == null ? "" : item.recordedAt().toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString((item.effectiveFrom() + "|" + recordedAt + "|" + item.eventId()).getBytes(StandardCharsets.UTF_8));
    }
    private static Cursor parseCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", 3);
            if (parts.length != 3) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), parts[1].isBlank() ? null : Instant.parse(parts[1]), parts[2]);
        } catch (RuntimeException invalid) { throw bad("cursor is invalid"); }
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public enum Granularity { DETAIL, WEEK, MONTH }
    public enum TimelineType { APPOINTMENT, SESSION, EXECUTION }
    public record TimelineQuery(Instant from, Instant to, Set<TimelineType> types, Granularity granularity, String cursor, Integer limit) { }
    public record SpecialistParticipantWorkspaceView(Instant generatedAt, ParticipantHeader participant, RelationshipView relationship,
                                                      List<String> capabilities, AppointmentView nextAppointment, ActivePlanView activePlan,
                                                      List<GoalView> goals, AdherenceSummaryView adherenceSummary, RecentProgressView recentProgress,
                                                      List<ActiveProblemView> activeProblems, List<AttentionItemView> attentionItems, List<String> quickActions) { }
    public record ParticipantHeader(UUID participantId, String displayName, String avatarReference, String contextLabel, String timeZoneId,
                                    List<String> availableActions) { }
    public record RelationshipView(String status, Instant startedAt) { }
    public record AppointmentView(UUID appointmentId, Instant startsAt, Instant endsAt, String type, String status, String shortPurpose) { }
    public record ActivePlanView(UUID planId, UUID activeRevisionId, String name, String status, Instant activatedAt, LocalDate validFrom,
                                 LocalDate validTo, int activeSessionCount, SessionFactView nextPlannedSession,
                                 ExecutionFactView lastCompletedSession, List<String> availableActions) { }
    public record SessionFactView(UUID sessionId, String title, Instant scheduledAt) { }
    public record ExecutionFactView(UUID attemptId, Instant completedAt, String status) { }
    public record GoalView(UUID goalId, String title, String type, String status, LocalDate targetDate, BigDecimal baseline, BigDecimal target,
                           BigDecimal latestValue, String unit, String improvementDirection, String dataQuality, List<String> availableActions) { }
    public record AdherenceSummaryView(String dataStatus, Instant from, Instant to, Integer plannedSessions, Integer startedSessions,
                                       Integer completedSessions, Integer skippedSessions, BigDecimal prescriptionCompletion, BigDecimal reportingCoverage) { }
    public record RecentProgressView(String dataStatus, Instant latestActivityAt, String latestExecutionState) { }
    public record ActiveProblemView(UUID problemId, String type, String priority, String status, String shortDescription,
                                    Instant effectiveAt, Instant recordedAt, String source, List<String> availableActions) { }
    public record AttentionItemView(UUID attentionId, String type, String priority, String status, String shortDescription,
                                    Instant createdAt, Instant dueAt, List<String> availableActions) { }
    public record ParticipantTimelineView(Instant generatedAt, Instant from, Instant to, Granularity granularity,
                                          List<ParticipantTimelineEvent> items, String nextCursor) { }
    public record ParticipantTimelineEvent(String eventId, String eventType, String category, String status,
                                           Instant effectiveFrom, Instant effectiveTo, Instant recordedAt, Instant updatedAt,
                                           String title, String summary, String importance, String sensitivity, UUID actor,
                                           String source, List<UUID> relatedGoalIds, UUID relatedPlanRevisionId,
                                           PlannedExecutionComparison plannedExecutionComparison, Measurement measurement,
                                           Problem problem, List<String> availableActions, EventDetail detail) { }
    public record EventDetail(String detailKind, String detailResourceId, Integer sourceEventCount,
                              Instant sourceFrom, Instant sourceTo) {
        public EventDetail(String detailKind, String detailResourceId) {
            this(detailKind, detailResourceId, null, null, null);
        }
    }
    public record PlannedExecutionComparison(PlannedSession planned, PerformedSession performed, String completionState,
                                             List<ExecutionDeviation> deviations) { }
    public record PlannedSession(UUID sessionId, String title, List<PlannedDose> doses) { }
    public record PlannedDose(UUID prescriptionId, UUID exerciseVersionId, Integer sets, Integer repetitions,
                              Integer durationSeconds, BigDecimal load, String loadUnit, BigDecimal distance,
                              String tempo, Integer restSeconds) { }
    public record PerformedSession(UUID plannedSessionId, String selectedVariant, String detailResourceId) { }
    public record ExecutionDeviation(String type, String plannedValue, String performedValue) { }
    public record Measurement(String metricCode, BigDecimal value, String unit) { }
    public record Problem(UUID problemId, String type, String status, String summary) { }
    private record Access(UUID specialistId, SpecialistAuthorizationPort.AuthorizationDecision decision) { }
    private record Cursor(Instant effectiveFrom, Instant recordedAt, String eventId) { }
    private record SessionOccurrence(PlanRevisionQueryPort.SessionSnapshot session, Instant effectiveAt) { }
}
