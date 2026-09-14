package com.motionecosystem.adherence;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.SessionSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Participant-facing projection composed only from the planning public port. */
@Service
@RequiredArgsConstructor
public class TodayAgendaService {

    private final CurrentAccountService accounts;
    private final ParticipantContextQueryPort participants;
    private final ParticipantClientPort participantClients;
    private final PlanRevisionQueryPort revisions;
    private final SessionExecutionProgressQueryPort progress;
    private final SessionSafetyDecisionQueryPort safety;
    private final RecoveryEpisodeService recovery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TodayAgendaView today(String subject) {
        CurrentAccount account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        }
        UUID participantId = participantClients.findParticipantIdByPrincipalAccountId(account.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant record not found"));
        Optional<ParticipantContextQueryPort.ParticipantRecordContext> participant = participants.findContextByParticipantId(participantId);
        if (participant.isEmpty()) {
            return new TodayAgendaView(null, null, null, List.of(), List.of(), "TIME_ZONE_REQUIRED", null);
        }
        ZoneId timeZone = participant.get().timeZone();
        Instant now = clock.instant();
        LocalDate localDate = now.atZone(timeZone).toLocalDate();
        List<PlanRevisionSnapshot> activeRevisions = revisions.findActiveRevisions(participantId);
        if (activeRevisions.isEmpty()) {
            return new TodayAgendaView(timeZone.getId(), localDate, null, List.of(), List.of(), "NO_ACTIVE_PLAN", recovery.current(subject));
        }
        PlanRevisionSnapshot primary = activeRevisions.getFirst();
        List<RevisionSession> todaySessions = activeRevisions.stream()
                .flatMap(revision -> revision.cycles().stream()
                        .flatMap(cycle -> cycle.microcycles().stream())
                        .flatMap(microcycle -> microcycle.sessions().stream())
                        .map(session -> new RevisionSession(revision, session)))
                .filter(item -> belongsToLocalDay(item.session(), localDate, timeZone)).toList();
        List<UUID> sessionIds = todaySessions.stream().map(item -> item.session().id()).toList();
        var executionProgress = progress.findForSessions(participantId, sessionIds);
        List<AgendaSessionView> sessions = todaySessions.stream()
                .map(item -> toView(item.revision(), item.session(), now, executionProgress.get(item.session().id()),
                        safety.evaluateForSessions(participantId, item.revision().revisionId(), List.of(item.session().id()), now).get(item.session().id())))
                .sorted(Comparator.comparing(AgendaSessionView::sortAt)
                        .thenComparing(AgendaSessionView::title).thenComparing(AgendaSessionView::sessionId))
                .toList();
        var recoveryView = recovery.current(subject);
        if (recoveryView != null) sessions = sessions.stream().map(item -> new AgendaSessionView(item.sessionId(), item.planRevisionId(), item.planId(), item.planTitle(), item.title(), item.expectedDurationMinutes(), item.scheduledDate(), item.availableFrom(), item.availableTo(), item.executionState(), item.doseSummary(), item.safetyState(), "RECOVERY_REQUIRED", item.sortAt())).toList();
        return new TodayAgendaView(timeZone.getId(), localDate,
                activePlan(primary), activeRevisions.stream().map(TodayAgendaService::activePlan).toList(),
                sessions, sessions.isEmpty() ? "NO_SESSION_TODAY" : "READY", recoveryView);
    }

    private static boolean belongsToLocalDay(SessionSnapshot session, LocalDate day, ZoneId zone) {
        if (session.scheduledDate() != null && session.scheduledDate().equals(day)) return true;
        return session.availableFrom() != null && session.availableFrom().atZone(zone).toLocalDate().equals(day);
    }

    private static AgendaSessionView toView(PlanRevisionSnapshot revision, SessionSnapshot session, Instant now,
            SessionExecutionProgressQueryPort.SessionExecutionProgress execution,
            SessionSafetyDecisionQueryPort.SessionSafetyDecision safetyDecision) {
        boolean inWindow = (session.availableFrom() == null || !now.isBefore(session.availableFrom()))
                && (session.availableTo() == null || !now.isAfter(session.availableTo()));
        String status = execution.state().name();
        String nextAction = isTerminal(status) ? "NONE" : safetyDecision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.BLOCKED
                ? "CONTACT_SPECIALIST" : (inWindow ? "START_SESSION" : "WAIT_FOR_WINDOW");
        Instant sortAt = session.availableFrom() == null ? Instant.MIN : session.availableFrom();
        return new AgendaSessionView(session.id(), revision.revisionId(), revision.planId(), null, session.title(), session.expectedDurationMinutes(),
                session.scheduledDate(), session.availableFrom(), session.availableTo(), status,
                session.prescriptions().size() + " prescriptions", safetyDecision.status().name(), nextAction, sortAt);
    }
    private static boolean isTerminal(String status) {
        return List.of("COMPLETED", "PARTIAL", "SKIPPED", "STOPPED").contains(status);
    }

    private static ActivePlanView activePlan(PlanRevisionSnapshot revision) {
        return new ActivePlanView(revision.planId(), revision.revisionId(), revision.revisionNumber());
    }

    private record RevisionSession(PlanRevisionSnapshot revision, SessionSnapshot session) { }

    public record TodayAgendaView(String timeZone, LocalDate localDate, ActivePlanView activePlan,
                                  List<ActivePlanView> activePlans,
                                  List<AgendaSessionView> sessions, String state, RecoveryEpisodeService.RecoveryView recovery) {
        public TodayAgendaView { activePlans = List.copyOf(activePlans); sessions = List.copyOf(sessions); }
    }

    public record ActivePlanView(UUID planId, UUID revisionId, int revisionNumber) { }

    public record AgendaSessionView(UUID sessionId, UUID planRevisionId, UUID planId, String planTitle, String title, int expectedDurationMinutes,
                                    LocalDate scheduledDate, Instant availableFrom, Instant availableTo,
                                    String executionState, String doseSummary, String safetyState,
                                    String nextAction, Instant sortAt) { }
}
