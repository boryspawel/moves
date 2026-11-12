package com.motionecosystem.application.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.motionecosystem.calendar.api.SpecialistAppointmentHistoryQueryPort;
import com.motionecosystem.calendar.api.SpecialistAppointmentHistoryQueryPort.AppointmentSnapshot;
import com.motionecosystem.calendar.api.SpecialistAppointmentHistoryQueryPort.LifecycleSnapshot;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.participantdocumentation.api.ParticipantDocumentationEventQueryPort;
import com.motionecosystem.participantgoals.api.SpecialistParticipantGoalHistoryQueryPort;
import com.motionecosystem.participantmeasurements.api.SpecialistMeasurementHistoryQueryPort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort.WorkspaceCapability;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort.WorkspacePurpose;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort.WorkspaceRole;
import com.motionecosystem.trainingexecution.api.SpecialistExecutionHistoryQueryPort;
import com.motionecosystem.trainingplanning.api.SpecialistPlanningHistoryQueryPort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Composes owner-authorized facts into top-level participant history items. */
@Service
public class SpecialistParticipantHistoryService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final String CURSOR_VERSION = "history-v1";

    private final CurrentAccountService accounts;
    private final SpecialistWorkspacePort workspace;
    private final SpecialistAppointmentHistoryQueryPort appointments;
    private final SpecialistPlanningHistoryQueryPort planning;
    private final SpecialistExecutionHistoryQueryPort executions;
    private final SpecialistMeasurementHistoryQueryPort measurements;
    private final ParticipantDocumentationEventQueryPort documentation;
    private final SpecialistParticipantGoalHistoryQueryPort goals;

    public SpecialistParticipantHistoryService(CurrentAccountService accounts, SpecialistWorkspacePort workspace,
            SpecialistAppointmentHistoryQueryPort appointments, SpecialistPlanningHistoryQueryPort planning,
            SpecialistExecutionHistoryQueryPort executions, SpecialistMeasurementHistoryQueryPort measurements,
            ParticipantDocumentationEventQueryPort documentation, SpecialistParticipantGoalHistoryQueryPort goals) {
        this.accounts = accounts;
        this.workspace = workspace;
        this.appointments = appointments;
        this.planning = planning;
        this.executions = executions;
        this.measurements = measurements;
        this.documentation = documentation;
        this.goals = goals;
    }

    public HistoryPage history(String subject, UUID canonicalParticipantId, ActingContext actingContext, HistoryQuery query) {
        NormalizedQuery normalized = normalize(subject, canonicalParticipantId, actingContext, query);
        authorizeEntry(subject, canonicalParticipantId, actingContext);
        Cursor cursor = decodeCursor(normalized, query == null ? null : query.cursor());

        List<AppointmentSnapshot> appointmentCandidates = appointments.appointmentCandidates(subject, canonicalParticipantId,
                actingContext, normalized.from(), normalized.to());
        Map<UUID, AppointmentSnapshot> appointmentsById = byId(appointmentCandidates, AppointmentSnapshot::appointmentId);
        Set<UUID> candidateAppointmentIds = appointmentsById.keySet();

        LocalDate sessionFrom = LocalDate.ofInstant(normalized.from(), ZoneOffset.UTC);
        LocalDate sessionTo = LocalDate.ofInstant(normalized.to(), ZoneOffset.UTC).plusDays(1);
        List<SpecialistPlanningHistoryQueryPort.SessionSnapshot> sessionCandidates = planning.sessionCandidates(subject,
                canonicalParticipantId, actingContext, sessionFrom, sessionTo).stream()
                .filter(session -> inRange(sessionHistoryAt(session), normalized)).toList();
        Set<UUID> sessionCandidateIds = ids(sessionCandidates, SpecialistPlanningHistoryQueryPort.SessionSnapshot::sessionId);
        Set<UUID> visibleSessionCandidateIds = planning.visibleSessionIds(subject, canonicalParticipantId, actingContext, sessionCandidateIds);

        List<SpecialistExecutionHistoryQueryPort.ExecutionFact> executionCandidates = executions.executionCandidates(subject,
                canonicalParticipantId, actingContext, normalized.from(), normalized.to());
        Set<UUID> executionSessionIds = ids(executionCandidates, SpecialistExecutionHistoryQueryPort.ExecutionFact::plannedSessionId);
        List<AppointmentSnapshot> executionParents = appointments.appointmentsByPlannedSessionIds(subject, canonicalParticipantId,
                actingContext, executionSessionIds);
        Map<UUID, List<AppointmentSnapshot>> executionParentsBySession = groupBy(executionParents, AppointmentSnapshot::plannedSessionId);
        Map<UUID, SpecialistExecutionHistoryQueryPort.ExecutionLink> executionLinks = byId(
                executions.executionLinksByPlannedSessionIds(subject, canonicalParticipantId, actingContext, executionSessionIds),
                SpecialistExecutionHistoryQueryPort.ExecutionLink::id);

        List<SpecialistMeasurementHistoryQueryPort.MeasurementFact> measurementCandidates = measurements.measurementCandidates(subject,
                canonicalParticipantId, actingContext, normalized.from(), normalized.to());
        Set<UUID> measurementAppointmentIds = ids(measurementCandidates, SpecialistMeasurementHistoryQueryPort.MeasurementFact::appointmentId);
        Set<UUID> validMeasurementParents = ids(appointments.appointmentsByIds(subject, canonicalParticipantId, actingContext,
                measurementAppointmentIds), AppointmentSnapshot::appointmentId);

        List<ParticipantDocumentationEventQueryPort.DocumentationHistoryCandidate> documentationCandidates = documentation
                .documentationCandidates(subject, canonicalParticipantId, actingContext, normalized.from(), normalized.to());
        Set<UUID> documentationAppointmentIds = ids(documentationCandidates,
                ParticipantDocumentationEventQueryPort.DocumentationHistoryCandidate::appointmentId);
        Set<UUID> validDocumentationParents = ids(appointments.appointmentsByIds(subject, canonicalParticipantId, actingContext,
                documentationAppointmentIds), AppointmentSnapshot::appointmentId);

        List<SpecialistParticipantGoalHistoryQueryPort.GoalHistoryCandidate> goalCandidates = goals.goalHistoryCandidates(subject,
                canonicalParticipantId, actingContext, normalized.from(), normalized.to());

        Set<UUID> appointmentSessionIds = ids(appointmentCandidates, AppointmentSnapshot::plannedSessionId);
        Map<UUID, List<AppointmentSnapshot>> authorizedAppointmentsBySession = groupBy(
                appointments.appointmentsByPlannedSessionIds(subject, canonicalParticipantId, actingContext, appointmentSessionIds),
                AppointmentSnapshot::plannedSessionId);
        Set<UUID> unambiguousAppointmentSessionIds = authorizedAppointmentsBySession.entrySet().stream()
                .filter(entry -> entry.getValue().size() == 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<UUID> visibleAppointmentSessionIds = planning.visibleSessionIds(subject, canonicalParticipantId, actingContext,
                appointmentSessionIds);
        Set<UUID> appointmentMeasurementIds = measurements.appointmentIdsWithMeasurements(subject, canonicalParticipantId,
                actingContext, candidateAppointmentIds);
        Set<UUID> appointmentDocumentationIds = documentation.appointmentIdsWithDocumentation(subject, canonicalParticipantId,
                actingContext, candidateAppointmentIds);
        Map<UUID, List<SpecialistExecutionHistoryQueryPort.ExecutionLink>> allExecutionLinksBySession = groupBy(
                executions.executionLinksByPlannedSessionIds(subject, canonicalParticipantId, actingContext, appointmentSessionIds),
                SpecialistExecutionHistoryQueryPort.ExecutionLink::plannedSessionId);

        List<Candidate> candidates = new ArrayList<>();
        for (AppointmentSnapshot appointment : appointmentCandidates) {
            Set<FilterKind> matched = matchedEncounterKinds(appointment, visibleAppointmentSessionIds, appointmentMeasurementIds,
                    appointmentDocumentationIds, allExecutionLinksBySession, unambiguousAppointmentSessionIds);
            if (matches(normalized.types(), matched)) candidates.add(Candidate.encounter(appointment, matched));
        }
        Set<UUID> linkedSessionIds = ids(appointments.appointmentsByPlannedSessionIds(subject, canonicalParticipantId, actingContext,
                visibleSessionCandidateIds), AppointmentSnapshot::plannedSessionId);
        for (SpecialistPlanningHistoryQueryPort.SessionSnapshot session : sessionCandidates) {
            if (visibleSessionCandidateIds.contains(session.sessionId()) && !linkedSessionIds.contains(session.sessionId())
                    && normalized.types().contains(FilterKind.SESSION)) candidates.add(Candidate.session(session));
        }
        for (SpecialistExecutionHistoryQueryPort.ExecutionFact execution : executionCandidates) {
            List<AppointmentSnapshot> parents = executionParentsBySession.getOrDefault(execution.plannedSessionId(), List.of());
            boolean belongsToEncounter = parents.size() == 1 && qualifies(parents.getFirst(), executionLinks.get(execution.id()));
            if (!belongsToEncounter && normalized.types().contains(FilterKind.EXECUTION)) candidates.add(Candidate.execution(execution));
        }
        for (SpecialistMeasurementHistoryQueryPort.MeasurementFact measurement : measurementCandidates) {
            if (!validMeasurementParents.contains(measurement.appointmentId()) && normalized.types().contains(FilterKind.MEASUREMENT)) {
                candidates.add(Candidate.measurement(measurement));
            }
        }
        for (ParticipantDocumentationEventQueryPort.DocumentationHistoryCandidate event : documentationCandidates) {
            boolean linked = "NOTE".equals(event.recordType()) && validDocumentationParents.contains(event.appointmentId());
            if (!linked && "NOTE".equals(event.recordType()) && normalized.types().contains(FilterKind.NOTE)) candidates.add(Candidate.note(event));
            if ("INTERVIEW".equals(event.recordType()) && normalized.types().contains(FilterKind.INTERVIEW)) candidates.add(Candidate.interview(event));
        }
        for (SpecialistParticipantGoalHistoryQueryPort.GoalHistoryCandidate goal : goalCandidates) {
            if (goal.sourceMeasurementId() == null && normalized.types().contains(FilterKind.GOAL)) candidates.add(Candidate.goal(goal));
        }

        List<Candidate> pageWithProbe = candidates.stream().collect(Collectors.toMap(Candidate::stableItemId, item -> item,
                (left, right) -> left, HashMap::new)).values().stream().sorted(Candidate.ORDER)
                .filter(item -> cursor == null || after(item, cursor)).limit(normalized.limit() + 1L).toList();
        boolean hasMore = pageWithProbe.size() > normalized.limit();
        List<Candidate> selected = hasMore ? pageWithProbe.subList(0, normalized.limit()) : pageWithProbe;
        Set<UUID> selectedAppointmentIds = selected.stream().filter(Candidate::encounter).map(Candidate::id).collect(Collectors.toSet());
        Map<UUID, EncounterData> encounterData = hydrateEncounters(subject, canonicalParticipantId, actingContext, selectedAppointmentIds);
        List<HistoryItem> items = selected.stream().map(candidate -> candidate.encounter()
                ? HistoryItem.encounter(candidate.stableItemId(), candidate.historyAt(), candidate.matchedKinds(), encounterData.get(candidate.id()))
                : HistoryItem.standalone(candidate.stableItemId(), candidate.historyAt(), candidate.matchedKinds(), candidate.standalone())).toList();
        String nextCursor = hasMore ? encodeCursor(normalized, selected.getLast()) : null;
        return new HistoryPage(List.copyOf(items), nextCursor);
    }

    /** Resolves a stable history identifier without depending on the current page or date range. */
    public HistoryResolution resolve(String subject, UUID participantId, ActingContext context, String eventId) {
        try {
            authorizeEntry(subject, participantId, context);
            Identifier identifier = Identifier.parse(eventId);
            return switch (identifier.kind()) {
                case ENCOUNTER, APPOINTMENT -> encounterResolution(subject, participantId, context, identifier.id(), null, null);
                case APPOINTMENT_EVENT -> lifecycleResolution(subject, participantId, context, identifier.id());
                case MEASUREMENT -> measurementResolution(subject, participantId, context, identifier.id());
                case NOTE, INTERVIEW -> documentationResolution(subject, participantId, context, identifier.id());
                case GOAL -> goalResolution(subject, participantId, context, identifier.id());
                case EXECUTION -> executionResolution(subject, participantId, context, identifier.id());
                case SESSION -> sessionResolution(subject, participantId, context, identifier.id());
            };
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) throw notFound();
            throw error;
        }
    }

    private HistoryResolution lifecycleResolution(String subject, UUID participant, ActingContext context, UUID eventId) {
        LifecycleSnapshot lifecycle = appointments.lifecycleByEventIds(subject, participant, context, Set.of(eventId)).stream().findFirst().orElseThrow(SpecialistParticipantHistoryService::notFound);
        return encounterResolution(subject, participant, context, lifecycle.appointmentId(), HighlightKind.LIFECYCLE, lifecycle.eventId());
    }

    private HistoryResolution measurementResolution(String subject, UUID participant, ActingContext context, UUID measurementId) {
        SpecialistMeasurementHistoryQueryPort.MeasurementFact measurement = measurements.measurementsByIds(subject, participant, context, Set.of(measurementId)).stream().findFirst().orElseThrow(SpecialistParticipantHistoryService::notFound);
        if (measurement.appointmentId() != null && !appointments.appointmentsByIds(subject, participant, context, Set.of(measurement.appointmentId())).isEmpty()) {
            return encounterResolution(subject, participant, context, measurement.appointmentId(), HighlightKind.MEASUREMENT, measurement.id());
        }
        HistoryItem item = HistoryItem.standalone("measurement:" + measurement.id(), measurement.measuredAt(), Set.of(FilterKind.MEASUREMENT),
                new StandaloneView(null, null, measurement(measurement), null, null, null));
        return new HistoryResolution(item, HighlightKind.MEASUREMENT, measurement.id(), null);
    }

    private HistoryResolution documentationResolution(String subject, UUID participant, ActingContext context, UUID eventId) {
        ParticipantDocumentationEventQueryPort.DocumentationHistoryCandidate event = documentation.documentationByEventIds(subject, participant, context, Set.of(eventId)).stream().findFirst().orElseThrow(SpecialistParticipantHistoryService::notFound);
        NavigationTarget target = new NavigationTarget("INTERVIEW".equals(event.recordType()) ? NavigationKind.DOCUMENTATION_INTERVIEW : NavigationKind.DOCUMENTATION_NOTE, event.recordId());
        if ("NOTE".equals(event.recordType()) && event.appointmentId() != null) {
            return encounterResolution(subject, participant, context, event.appointmentId(), HighlightKind.DOCUMENTATION, event.recordId(), target);
        }
        StandaloneView standalone = "INTERVIEW".equals(event.recordType())
                ? new StandaloneView(null, null, null, null, new InterviewEventView(event.eventId(), event.recordId(), event.eventType(), event.status(), event.effectiveAt(), event.recordedAt()), null)
                : new StandaloneView(null, null, null, new NoteEventView(event.eventId(), event.recordId(), event.eventType(), event.status(), event.category(), event.effectiveAt(), event.recordedAt()), null, null);
        FilterKind type = "INTERVIEW".equals(event.recordType()) ? FilterKind.INTERVIEW : FilterKind.NOTE;
        String prefix = type == FilterKind.INTERVIEW ? "interview-event:" : "note-event:";
        return new HistoryResolution(HistoryItem.standalone(prefix + event.eventId(), event.effectiveAt(), Set.of(type), standalone), HighlightKind.DOCUMENTATION, event.recordId(), target);
    }

    private HistoryResolution goalResolution(String subject, UUID participant, ActingContext context, UUID eventId) {
        SpecialistParticipantGoalHistoryQueryPort.GoalHistoryCandidate goal = goals.goalHistoryByEventIds(subject, participant, context, Set.of(eventId)).stream().findFirst().orElseThrow(SpecialistParticipantHistoryService::notFound);
        StandaloneView standalone = new StandaloneView(null, null, null, null, null,
                new GoalEventView(goal.eventId(), goal.goalId(), goal.outcomeId(), goal.eventType(), goal.status(), goal.effectiveAt(), goal.recordedAt()));
        return new HistoryResolution(HistoryItem.standalone("goal-event:" + goal.eventId(), goal.effectiveAt(), Set.of(FilterKind.GOAL), standalone), HighlightKind.GOAL, goal.goalId(), new NavigationTarget(NavigationKind.GOAL, goal.goalId()));
    }

    private HistoryResolution executionResolution(String subject, UUID participant, ActingContext context, UUID executionId) {
        SpecialistExecutionHistoryQueryPort.ExecutionFact execution = executions.executionsByIds(subject, participant, context, Set.of(executionId)).stream().findFirst().orElseThrow(SpecialistParticipantHistoryService::notFound);
        if (execution.plannedSessionId() == null) {
            HistoryItem item = HistoryItem.standalone("execution:" + execution.id(), execution.recordedAt(), Set.of(FilterKind.EXECUTION),
                    new StandaloneView(null, execution(execution), null, null, null, null));
            return new HistoryResolution(item, null, null, null);
        }
        List<AppointmentSnapshot> parents = appointments.appointmentsByPlannedSessionIds(subject, participant, context, Set.of(execution.plannedSessionId()));
        if (parents.size() == 1 && qualifies(parents.getFirst(), execution)) return encounterResolution(subject, participant, context, parents.getFirst().appointmentId(), null, null);
        HistoryItem item = HistoryItem.standalone("execution:" + execution.id(), execution.recordedAt(), Set.of(FilterKind.EXECUTION),
                new StandaloneView(null, execution(execution), null, null, null, null));
        return new HistoryResolution(item, null, null, null);
    }

    private HistoryResolution sessionResolution(String subject, UUID participant, ActingContext context, UUID sessionId) {
        SpecialistPlanningHistoryQueryPort.SessionSnapshot session = planning.sessionsByIds(subject, participant, context, Set.of(sessionId)).stream().findFirst().orElseThrow(SpecialistParticipantHistoryService::notFound);
        List<AppointmentSnapshot> parents = appointments.appointmentsByPlannedSessionIds(subject, participant, context, Set.of(sessionId));
        if (parents.size() == 1) return encounterResolution(subject, participant, context, parents.getFirst().appointmentId(), null, null);
        HistoryItem item = HistoryItem.standalone("session:" + session.sessionId(), sessionHistoryAt(session), Set.of(FilterKind.SESSION),
                new StandaloneView(session(session), null, null, null, null, null));
        return new HistoryResolution(item, null, null, null);
    }

    private HistoryResolution encounterResolution(String subject, UUID participant, ActingContext context, UUID appointmentId,
                                                  HighlightKind highlightKind, UUID highlightId) {
        return encounterResolution(subject, participant, context, appointmentId, highlightKind, highlightId, null);
    }

    private HistoryResolution encounterResolution(String subject, UUID participant, ActingContext context, UUID appointmentId,
                                                  HighlightKind highlightKind, UUID highlightId, NavigationTarget target) {
        AppointmentSnapshot appointment = appointments.appointmentsByIds(subject, participant, context, Set.of(appointmentId)).stream().findFirst().orElseThrow(SpecialistParticipantHistoryService::notFound);
        EncounterData data = hydrateEncounters(subject, participant, context, Set.of(appointmentId)).get(appointmentId);
        if (data == null) throw notFound();
        return new HistoryResolution(HistoryItem.encounter("encounter:" + appointmentId, appointment.startsAt(), Set.of(FilterKind.APPOINTMENT), data), highlightKind, highlightId, target);
    }

    private Map<UUID, EncounterData> hydrateEncounters(String subject, UUID participant, ActingContext context, Set<UUID> appointmentIds) {
        if (appointmentIds.isEmpty()) return Map.of();
        Map<UUID, AppointmentSnapshot> selected = byId(appointments.appointmentsByIds(subject, participant, context, appointmentIds), AppointmentSnapshot::appointmentId);
        Set<UUID> sessionIds = ids(selected.values(), AppointmentSnapshot::plannedSessionId);
        Map<UUID, List<AppointmentSnapshot>> selectedAppointmentsBySession = groupBy(
                appointments.appointmentsByPlannedSessionIds(subject, participant, context, sessionIds), AppointmentSnapshot::plannedSessionId);
        Map<UUID, SpecialistPlanningHistoryQueryPort.SessionSnapshot> sessions = byId(planning.sessionsByIds(subject, participant, context, sessionIds), SpecialistPlanningHistoryQueryPort.SessionSnapshot::sessionId);
        Map<UUID, List<LifecycleSnapshot>> lifecycles = groupBy(appointments.lifecycleByAppointmentIds(subject, participant, context, selected.keySet()), LifecycleSnapshot::appointmentId);
        Map<UUID, List<SpecialistMeasurementHistoryQueryPort.MeasurementFact>> measured = groupBy(measurements.measurementsByAppointmentIds(subject, participant, context, selected.keySet()), SpecialistMeasurementHistoryQueryPort.MeasurementFact::appointmentId);
        Map<UUID, List<ParticipantDocumentationEventQueryPort.DocumentationReference>> documented = groupBy(documentation.documentationByAppointmentIds(subject, participant, context, selected.keySet()), ParticipantDocumentationEventQueryPort.DocumentationReference::appointmentId);
        List<SpecialistExecutionHistoryQueryPort.ExecutionFact> executionFacts = executions.executionsByPlannedSessionIds(subject, participant, context, sessionIds);
        Map<UUID, List<SpecialistExecutionHistoryQueryPort.ExecutionFact>> executionBySession = groupBy(executionFacts, SpecialistExecutionHistoryQueryPort.ExecutionFact::plannedSessionId);
        Map<UUID, EncounterData> result = new HashMap<>();
        for (AppointmentSnapshot appointment : selected.values()) {
            List<LifecycleView> lifecycle = sortedLifecycle(lifecycles.get(appointment.appointmentId()));
            boolean unambiguousSession = selectedAppointmentsBySession.getOrDefault(appointment.plannedSessionId(), List.of()).size() == 1;
            List<ExecutionView> qualifyingExecutions = (unambiguousSession ? executionBySession.getOrDefault(appointment.plannedSessionId(), List.of()) : List.<SpecialistExecutionHistoryQueryPort.ExecutionFact>of()).stream()
                    .filter(execution -> qualifies(appointment, execution)).map(SpecialistParticipantHistoryService::execution).sorted(Comparator.comparing(ExecutionView::recordedAt).thenComparing(ExecutionView::id)).toList();
            LifecycleView completion = lifecycle.stream().filter(item -> "COMPLETED".equals(item.toStatus())).max(Comparator
                    .comparing(LifecycleView::effectiveAt).thenComparing(LifecycleView::recordedAt).thenComparing(LifecycleView::id)).orElse(null);
            result.put(appointment.appointmentId(), new EncounterData(appointment(appointment), lifecycle,
                    session( sessions.get(appointment.plannedSessionId())), qualifyingExecutions,
                    sortedMeasurements(measured.get(appointment.appointmentId())),
                    sortedDocumentation(documented.get(appointment.appointmentId())), completion));
        }
        return result;
    }

    private Set<FilterKind> matchedEncounterKinds(AppointmentSnapshot appointment, Set<UUID> visibleSessions,
            Set<UUID> measurementAppointments, Set<UUID> documentationAppointments,
            Map<UUID, List<SpecialistExecutionHistoryQueryPort.ExecutionLink>> executionLinks, Set<UUID> unambiguousSessions) {
        EnumSet<FilterKind> matched = EnumSet.of(FilterKind.APPOINTMENT);
        if (visibleSessions.contains(appointment.plannedSessionId())) matched.add(FilterKind.SESSION);
        if (measurementAppointments.contains(appointment.appointmentId())) matched.add(FilterKind.MEASUREMENT);
        if (documentationAppointments.contains(appointment.appointmentId())) matched.add(FilterKind.NOTE);
        if (unambiguousSessions.contains(appointment.plannedSessionId()) && executionLinks.getOrDefault(appointment.plannedSessionId(), List.of()).stream().anyMatch(link -> qualifies(appointment, link))) matched.add(FilterKind.EXECUTION);
        return Set.copyOf(matched);
    }

    private static boolean qualifies(AppointmentSnapshot appointment, SpecialistExecutionHistoryQueryPort.ExecutionLink execution) {
        return execution != null && appointment.plannedSessionId() != null && appointment.plannedSessionId().equals(execution.plannedSessionId())
                && appointment.participantId().equals(execution.participantId()) && appointment.specialistAccountId().equals(execution.recorderAccountId())
                && "SPECIALIST".equals(execution.recordingSource());
    }

    private static boolean qualifies(AppointmentSnapshot appointment, SpecialistExecutionHistoryQueryPort.ExecutionFact execution) {
        return appointment.plannedSessionId() != null && appointment.plannedSessionId().equals(execution.plannedSessionId())
                && appointment.participantId().equals(execution.participantId()) && appointment.specialistAccountId().equals(execution.recorderAccountId())
                && "SPECIALIST".equals(execution.recordingSource());
    }

    private void authorizeEntry(String subject, UUID participant, ActingContext context) {
        if (participant == null) throw bad("participantId is required");
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required");
        WorkspaceRole role = context == null || context.role() == null ? null : workspace.findProfile(account.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "specialist profile is required")).role();
        if (role == null || (role == WorkspaceRole.TRAINER) != (context.role() == ProfessionalRole.TRAINER)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "acting context is required");
        workspace.requireParticipantCapabilities(account.id(), participant, role,
                Set.of(role == WorkspaceRole.TRAINER ? WorkspaceCapability.PLAN_PERFORMANCE : WorkspaceCapability.PLAN_FUNCTIONAL_RECOVERY),
                role == WorkspaceRole.TRAINER ? WorkspacePurpose.PERFORMANCE_PLANNING : WorkspacePurpose.FUNCTIONAL_RECOVERY);
    }

    private static NormalizedQuery normalize(String subject, UUID participant, ActingContext context, HistoryQuery query) {
        if (query == null || context == null || context.role() == null || query.fromInclusive() == null || query.toExclusive() == null || !query.toExclusive().isAfter(query.fromInclusive())) throw bad("history range and acting context are required");
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        if (limit < 1 || limit > MAX_LIMIT) throw bad("history limit must be between 1 and 100");
        Set<FilterKind> kinds = query.types() == null || query.types().isEmpty() ? EnumSet.allOf(FilterKind.class) : EnumSet.copyOf(query.types());
        return new NormalizedQuery(subject, participant, context.role(), query.fromInclusive(), query.toExclusive(), Set.copyOf(kinds), limit);
    }

    private static boolean matches(Set<FilterKind> selected, Set<FilterKind> matched) { return matched.stream().anyMatch(selected::contains); }
    private static boolean inRange(Instant instant, NormalizedQuery query) { return !instant.isBefore(query.from()) && instant.isBefore(query.to()); }
    private static Instant sessionHistoryAt(SpecialistPlanningHistoryQueryPort.SessionSnapshot session) { return session.availableFrom() != null ? session.availableFrom() : session.scheduledDate().atStartOfDay().toInstant(ZoneOffset.UTC); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "history item not found"); }

    private static Cursor decodeCursor(NormalizedQuery query, String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(encoded)))) {
            String version = input.readUTF(); String subject = input.readUTF(); UUID participant = UUID.fromString(input.readUTF());
            ProfessionalRole role = ProfessionalRole.valueOf(input.readUTF()); Instant from = Instant.parse(input.readUTF()); Instant to = Instant.parse(input.readUTF());
            String encodedKinds = input.readUTF();
            Set<FilterKind> kinds = encodedKinds.isEmpty() ? Set.of() : EnumSet.copyOf(List.of(encodedKinds.split(",")).stream().map(FilterKind::valueOf).toList());
            Instant at = Instant.parse(input.readUTF()); String itemId = input.readUTF();
            Cursor cursor = new Cursor(version, subject, participant, role, from, to, kinds, at, itemId);
            if (!cursor.matches(query)) throw bad("history cursor does not match query");
            return cursor;
        } catch (IllegalArgumentException | IOException error) { throw bad("invalid history cursor"); }
    }

    private static String encodeCursor(NormalizedQuery query, Candidate last) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(CURSOR_VERSION); output.writeUTF(query.subject()); output.writeUTF(query.participant().toString()); output.writeUTF(query.role().name());
            output.writeUTF(query.from().toString()); output.writeUTF(query.to().toString()); output.writeUTF(query.types().stream().sorted().map(Enum::name).collect(Collectors.joining(",")));
            output.writeUTF(last.historyAt().toString()); output.writeUTF(last.stableItemId()); output.flush();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException error) { throw new IllegalStateException("cannot encode history cursor", error); }
    }

    private static boolean after(Candidate item, Cursor cursor) { return item.historyAt().isBefore(cursor.historyAt()) || item.historyAt().equals(cursor.historyAt()) && item.stableItemId().compareTo(cursor.itemId()) < 0; }
    private static <T> Set<UUID> ids(Collection<T> source, java.util.function.Function<T, UUID> id) { return source.stream().map(id).filter(java.util.Objects::nonNull).collect(Collectors.toSet()); }
    private static <T> Map<UUID, T> byId(Collection<T> source, java.util.function.Function<T, UUID> id) { return source.stream().filter(item -> id.apply(item) != null).collect(Collectors.toMap(id, item -> item, (left, right) -> left)); }
    private static <T> Map<UUID, List<T>> groupBy(Collection<T> source, java.util.function.Function<T, UUID> key) { return source.stream().filter(item -> key.apply(item) != null).collect(Collectors.groupingBy(key)); }
    private static List<LifecycleView> sortedLifecycle(List<LifecycleSnapshot> source) { return source == null ? List.of() : source.stream().map(SpecialistParticipantHistoryService::lifecycle).sorted(Comparator.comparing(LifecycleView::effectiveAt).thenComparing(LifecycleView::recordedAt).thenComparing(LifecycleView::id)).toList(); }
    private static List<MeasurementView> sortedMeasurements(List<SpecialistMeasurementHistoryQueryPort.MeasurementFact> source) { return source == null ? List.of() : source.stream().map(SpecialistParticipantHistoryService::measurement).sorted(Comparator.comparing(MeasurementView::measuredAt).thenComparing(MeasurementView::recordedAt).thenComparing(MeasurementView::id)).toList(); }
    private static List<DocumentationView> sortedDocumentation(List<ParticipantDocumentationEventQueryPort.DocumentationReference> source) { return source == null ? List.of() : source.stream().map(SpecialistParticipantHistoryService::documentation).sorted(Comparator.comparing(DocumentationView::createdAt).thenComparing(DocumentationView::updatedAt).thenComparing(DocumentationView::id)).toList(); }

    private static AppointmentView appointment(AppointmentSnapshot item) { return new AppointmentView(item.appointmentId(), item.specialistAccountId(), item.participantId(), item.startsAt(), item.endsAt(), item.type(), item.status(), item.plannedSessionId()); }
    private static LifecycleView lifecycle(LifecycleSnapshot item) { return new LifecycleView(item.eventId(), item.effectiveAt(), item.recordedAt(), item.type(), item.fromStatus(), item.toStatus()); }
    private static SessionView session(SpecialistPlanningHistoryQueryPort.SessionSnapshot item) { return item == null ? null : new SessionView(item.sessionId(), item.planId(), item.revisionId(), item.status(), item.kind(), item.scheduledDate(), item.availableFrom(), item.availableTo()); }
    private static ExecutionView execution(SpecialistExecutionHistoryQueryPort.ExecutionFact item) { return new ExecutionView(item.id(), item.plannedSessionId(), item.attemptId(), item.recordingSource(), item.recorderAccountId(), item.outcome(), item.recordedAt(), item.declaredCompletion(), item.resultCount()); }
    private static MeasurementView measurement(SpecialistMeasurementHistoryQueryPort.MeasurementFact item) { return new MeasurementView(item.id(), item.appointmentId(), item.metricCode(), item.value(), item.unit(), item.measurementMethod(), item.measuredAt(), item.recordedAt(), item.source()); }
    private static DocumentationView documentation(ParticipantDocumentationEventQueryPort.DocumentationReference item) { return new DocumentationView(item.recordId(), item.category(), item.status(), item.createdAt(), item.updatedAt()); }

    public enum ItemKind { ENCOUNTER, STANDALONE }
    public enum FilterKind { APPOINTMENT, SESSION, EXECUTION, MEASUREMENT, NOTE, INTERVIEW, GOAL }
    public enum HighlightKind { LIFECYCLE, MEASUREMENT, DOCUMENTATION, GOAL }
    public enum NavigationKind { DOCUMENTATION_NOTE, DOCUMENTATION_INTERVIEW, GOAL }
    @Schema(name = "SpecialistParticipantHistoryQuery")
    public record HistoryQuery(Instant fromInclusive, Instant toExclusive, Set<FilterKind> types, Integer limit, String cursor) { }
    @Schema(name = "SpecialistParticipantHistoryPage")
    public record HistoryPage(List<HistoryItem> items, String nextCursor) { public HistoryPage { items = List.copyOf(items); } }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "SpecialistParticipantHistoryResolution")
    public record HistoryResolution(HistoryItem item, HighlightKind highlightKind, UUID highlightId, NavigationTarget navigationTarget) { }
    @Schema(name = "SpecialistParticipantHistoryNavigationTarget")
    public record NavigationTarget(NavigationKind kind, UUID id) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "SpecialistParticipantHistoryItem")
    public record HistoryItem(ItemKind kind, String stableItemId, Instant historyAt, Set<FilterKind> matchedKinds, EncounterView encounter, StandaloneView standalone) {
        static HistoryItem encounter(String id, Instant at, Set<FilterKind> kinds, EncounterData data) { return new HistoryItem(ItemKind.ENCOUNTER, id, at, kinds, data.view(), null); }
        static HistoryItem standalone(String id, Instant at, Set<FilterKind> kinds, StandaloneView standalone) { return new HistoryItem(ItemKind.STANDALONE, id, at, kinds, null, standalone); }
        public HistoryItem { matchedKinds = Set.copyOf(matchedKinds); }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "SpecialistParticipantHistoryEncounter")
    public record EncounterView(AppointmentView appointment, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<LifecycleView> lifecycle, SessionView linkedSession, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ExecutionView> executions, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<MeasurementView> measurements, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DocumentationView> documentation, LifecycleView completion) { public EncounterView { lifecycle = List.copyOf(lifecycle); executions = List.copyOf(executions); measurements = List.copyOf(measurements); documentation = List.copyOf(documentation); } }
    @Schema(name = "HistoryAppointmentView")
    public record AppointmentView(UUID id, UUID specialistAccountId, UUID participantId, Instant startsAt, Instant endsAt, String type, String status, UUID plannedSessionId) { }
    @Schema(name = "HistoryLifecycleView")
    public record LifecycleView(UUID id, Instant effectiveAt, Instant recordedAt, String type, String fromStatus, String toStatus) { }
    @Schema(name = "HistorySessionView")
    public record SessionView(UUID id, UUID planId, UUID revisionId, String status, String kind, LocalDate scheduledDate, Instant availableFrom, Instant availableTo) { }
    @Schema(name = "HistoryExecutionView")
    public record ExecutionView(UUID id, UUID plannedSessionId, UUID attemptId, String source, UUID recorderAccountId, String outcome, Instant recordedAt, boolean declaredCompletion, long resultCount) { }
    @Schema(name = "HistoryMeasurementView")
    public record MeasurementView(UUID id, UUID appointmentId, String metricCode, java.math.BigDecimal value, String unit, String measurementMethod, Instant measuredAt, Instant recordedAt, String source) { }
    @Schema(name = "HistoryDocumentationView")
    public record DocumentationView(UUID id, String category, String status, Instant createdAt, Instant updatedAt) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "SpecialistParticipantHistoryStandalone")
    public record StandaloneView(SessionView session, ExecutionView execution, MeasurementView measurement, NoteEventView note, InterviewEventView interview, GoalEventView goal) { }
    @Schema(name = "HistoryNoteEventView")
    public record NoteEventView(UUID eventId, UUID recordId, String eventType, String status, String category, Instant effectiveAt, Instant recordedAt) { }
    @Schema(name = "HistoryInterviewEventView")
    public record InterviewEventView(UUID eventId, UUID recordId, String eventType, String status, Instant effectiveAt, Instant recordedAt) { }
    @Schema(name = "HistoryGoalEventView")
    public record GoalEventView(UUID eventId, UUID goalId, UUID outcomeId, String eventType, String status, Instant effectiveAt, Instant recordedAt) { }

    private record EncounterData(AppointmentView appointment, List<LifecycleView> lifecycle, SessionView session, List<ExecutionView> executions, List<MeasurementView> measurements, List<DocumentationView> documentation, LifecycleView completion) { EncounterView view() { return new EncounterView(appointment, lifecycle, session, executions, measurements, documentation, completion); } }
    private record NormalizedQuery(String subject, UUID participant, ProfessionalRole role, Instant from, Instant to, Set<FilterKind> types, int limit) { }
    private record Cursor(String version, String subject, UUID participant, ProfessionalRole role, Instant from, Instant to, Set<FilterKind> types, Instant historyAt, String itemId) { boolean matches(NormalizedQuery query) { return CURSOR_VERSION.equals(version) && subject.equals(query.subject()) && participant.equals(query.participant()) && role == query.role() && from.equals(query.from()) && to.equals(query.to()) && types.equals(query.types()); } }
    private record Candidate(String stableItemId, Instant historyAt, UUID id, boolean encounter, Set<FilterKind> matchedKinds, StandaloneView standalone) {
        static final Comparator<Candidate> ORDER = Comparator.comparing(Candidate::historyAt).reversed().thenComparing(Candidate::stableItemId, Comparator.reverseOrder());
        static Candidate encounter(AppointmentSnapshot value, Set<FilterKind> kinds) { return new Candidate("encounter:" + value.appointmentId(), value.startsAt(), value.appointmentId(), true, kinds, null); }
        static Candidate session(SpecialistPlanningHistoryQueryPort.SessionSnapshot value) { return new Candidate("session:" + value.sessionId(), sessionHistoryAt(value), value.sessionId(), false, Set.of(FilterKind.SESSION), new StandaloneView(SpecialistParticipantHistoryService.session(value), null, null, null, null, null)); }
        static Candidate execution(SpecialistExecutionHistoryQueryPort.ExecutionFact value) { return new Candidate("execution:" + value.id(), value.recordedAt(), value.id(), false, Set.of(FilterKind.EXECUTION), new StandaloneView(null, SpecialistParticipantHistoryService.execution(value), null, null, null, null)); }
        static Candidate measurement(SpecialistMeasurementHistoryQueryPort.MeasurementFact value) { return new Candidate("measurement:" + value.id(), value.measuredAt(), value.id(), false, Set.of(FilterKind.MEASUREMENT), new StandaloneView(null, null, SpecialistParticipantHistoryService.measurement(value), null, null, null)); }
        static Candidate note(ParticipantDocumentationEventQueryPort.DocumentationHistoryCandidate value) { return new Candidate("note-event:" + value.eventId(), value.effectiveAt(), value.eventId(), false, Set.of(FilterKind.NOTE), new StandaloneView(null, null, null, new NoteEventView(value.eventId(), value.recordId(), value.eventType(), value.status(), value.category(), value.effectiveAt(), value.recordedAt()), null, null)); }
        static Candidate interview(ParticipantDocumentationEventQueryPort.DocumentationHistoryCandidate value) { return new Candidate("interview-event:" + value.eventId(), value.effectiveAt(), value.eventId(), false, Set.of(FilterKind.INTERVIEW), new StandaloneView(null, null, null, null, new InterviewEventView(value.eventId(), value.recordId(), value.eventType(), value.status(), value.effectiveAt(), value.recordedAt()), null)); }
        static Candidate goal(SpecialistParticipantGoalHistoryQueryPort.GoalHistoryCandidate value) { return new Candidate("goal-event:" + value.eventId(), value.effectiveAt(), value.eventId(), false, Set.of(FilterKind.GOAL), new StandaloneView(null, null, null, null, null, new GoalEventView(value.eventId(), value.goalId(), value.outcomeId(), value.eventType(), value.status(), value.effectiveAt(), value.recordedAt()))); }
    }
    private enum IdentifierKind { ENCOUNTER, APPOINTMENT, APPOINTMENT_EVENT, SESSION, EXECUTION, MEASUREMENT, NOTE, INTERVIEW, GOAL }
    private record Identifier(IdentifierKind kind, UUID id) {
        static Identifier parse(String value) {
            if (value == null) throw notFound();
            String[] parts = value.split(":", -1);
            if (parts.length != 2) throw notFound();
            IdentifierKind kind = switch (parts[0]) {
                case "encounter" -> IdentifierKind.ENCOUNTER;
                case "appointment" -> IdentifierKind.APPOINTMENT;
                case "appointment-event" -> IdentifierKind.APPOINTMENT_EVENT;
                case "session" -> IdentifierKind.SESSION;
                case "execution", "session-execution" -> IdentifierKind.EXECUTION;
                case "measurement", "participant-measurement" -> IdentifierKind.MEASUREMENT;
                case "note-event", "participant-note-event" -> IdentifierKind.NOTE;
                case "interview-event", "participant-interview-event" -> IdentifierKind.INTERVIEW;
                case "goal-event", "participant-goal-event" -> IdentifierKind.GOAL;
                default -> throw notFound();
            };
            try { return new Identifier(kind, UUID.fromString(parts[1])); }
            catch (IllegalArgumentException invalid) { throw notFound(); }
        }
    }
}
