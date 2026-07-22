package com.motionecosystem.adherence;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
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
    private final PlanRevisionQueryPort revisions;
    private final SessionExecutionProgressQueryPort progress;
    private final SessionSafetyDecisionQueryPort safety;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TodayAgendaView today(String subject) {
        CurrentAccount account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        }
        Optional<ParticipantContextQueryPort.ParticipantContext> participant = participants.findContext(account.id());
        if (participant.isEmpty()) {
            return new TodayAgendaView(null, null, null, List.of(), "TIME_ZONE_REQUIRED");
        }
        ZoneId timeZone = participant.get().timeZone();
        Instant now = clock.instant();
        LocalDate localDate = now.atZone(timeZone).toLocalDate();
        Optional<PlanRevisionSnapshot> revision = revisions.findActiveRevision(account.id());
        if (revision.isEmpty()) {
            return new TodayAgendaView(timeZone.getId(), localDate, null, List.of(), "NO_ACTIVE_PLAN");
        }
        PlanRevisionSnapshot snapshot = revision.get();
        List<SessionSnapshot> todaySessions = snapshot.cycles().stream()
                .flatMap(cycle -> cycle.microcycles().stream())
                .flatMap(microcycle -> microcycle.sessions().stream())
                .filter(session -> belongsToLocalDay(session, localDate, timeZone)).toList();
        List<UUID> sessionIds = todaySessions.stream().map(SessionSnapshot::id).toList();
        var executionProgress = progress.findForSessions(account.id(), sessionIds);
        var safetyDecisions = safety.evaluateForSessions(account.id(), snapshot.revisionId(), sessionIds, now);
        List<AgendaSessionView> sessions = todaySessions.stream()
                .map(session -> toView(session, now, executionProgress.get(session.id()), safetyDecisions.get(session.id())))
                .sorted(Comparator.comparing(AgendaSessionView::sortAt)
                        .thenComparing(AgendaSessionView::title).thenComparing(AgendaSessionView::sessionId))
                .toList();
        return new TodayAgendaView(timeZone.getId(), localDate,
                new ActivePlanView(snapshot.planId(), snapshot.revisionId(), snapshot.revisionNumber()),
                sessions, sessions.isEmpty() ? "NO_SESSION_TODAY" : "READY");
    }

    private static boolean belongsToLocalDay(SessionSnapshot session, LocalDate day, ZoneId zone) {
        if (session.scheduledDate() != null && session.scheduledDate().equals(day)) return true;
        return session.availableFrom() != null && session.availableFrom().atZone(zone).toLocalDate().equals(day);
    }

    private static AgendaSessionView toView(SessionSnapshot session, Instant now,
            SessionExecutionProgressQueryPort.SessionExecutionProgress execution,
            SessionSafetyDecisionQueryPort.SessionSafetyDecision safetyDecision) {
        boolean inWindow = (session.availableFrom() == null || !now.isBefore(session.availableFrom()))
                && (session.availableTo() == null || !now.isAfter(session.availableTo()));
        String status = execution.state().name();
        String nextAction = safetyDecision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.BLOCKED
                ? "CONTACT_SPECIALIST" : (inWindow ? "START_SESSION" : "WAIT_FOR_WINDOW");
        Instant sortAt = session.availableFrom() == null ? Instant.MIN : session.availableFrom();
        return new AgendaSessionView(session.id(), session.title(), session.expectedDurationMinutes(),
                session.scheduledDate(), session.availableFrom(), session.availableTo(), status,
                session.prescriptions().size() + " prescriptions", safetyDecision.status().name(), nextAction, sortAt);
    }

    public record TodayAgendaView(String timeZone, LocalDate localDate, ActivePlanView activePlan,
                                  List<AgendaSessionView> sessions, String state) {
        public TodayAgendaView { sessions = List.copyOf(sessions); }
    }

    public record ActivePlanView(UUID planId, UUID revisionId, int revisionNumber) { }

    public record AgendaSessionView(UUID sessionId, String title, int expectedDurationMinutes,
                                    LocalDate scheduledDate, Instant availableFrom, Instant availableTo,
                                    String executionState, String doseSummary, String safetyState,
                                    String nextAction, Instant sortAt) { }
}
