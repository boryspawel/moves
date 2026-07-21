package com.motionecosystem.adherence;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
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

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");

    private final CurrentAccountService accounts;
    private final PlanRevisionQueryPort revisions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TodayAgendaView today(String subject) {
        CurrentAccount account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        }
        Instant now = clock.instant();
        LocalDate localDate = now.atZone(DEFAULT_ZONE).toLocalDate();
        Optional<PlanRevisionSnapshot> revision = revisions.findActiveRevision(account.id());
        if (revision.isEmpty()) {
            return new TodayAgendaView(DEFAULT_ZONE.getId(), localDate, null, List.of(), "NO_ACTIVE_PLAN");
        }
        PlanRevisionSnapshot snapshot = revision.get();
        List<AgendaSessionView> sessions = snapshot.cycles().stream()
                .flatMap(cycle -> cycle.microcycles().stream())
                .flatMap(microcycle -> microcycle.sessions().stream())
                .filter(session -> belongsToLocalDay(session, localDate, DEFAULT_ZONE))
                .map(session -> toView(session, now))
                .sorted(Comparator.comparing(AgendaSessionView::sortAt)
                        .thenComparing(AgendaSessionView::title).thenComparing(AgendaSessionView::sessionId))
                .toList();
        return new TodayAgendaView(DEFAULT_ZONE.getId(), localDate,
                new ActivePlanView(snapshot.planId(), snapshot.revisionId(), snapshot.revisionNumber()),
                sessions, sessions.isEmpty() ? "NO_SESSION_TODAY" : "READY");
    }

    private static boolean belongsToLocalDay(SessionSnapshot session, LocalDate day, ZoneId zone) {
        if (session.scheduledDate() != null && session.scheduledDate().equals(day)) return true;
        return session.availableFrom() != null && session.availableFrom().atZone(zone).toLocalDate().equals(day);
    }

    private static AgendaSessionView toView(SessionSnapshot session, Instant now) {
        boolean inWindow = (session.availableFrom() == null || !now.isBefore(session.availableFrom()))
                && (session.availableTo() == null || !now.isAfter(session.availableTo()));
        String status = inWindow ? "AVAILABLE" : "PAUSED";
        String nextAction = inWindow ? "START_SESSION" : "WAIT_FOR_WINDOW";
        Instant sortAt = session.availableFrom() == null ? Instant.MIN : session.availableFrom();
        return new AgendaSessionView(session.id(), session.title(), session.expectedDurationMinutes(),
                session.scheduledDate(), session.availableFrom(), session.availableTo(), status,
                session.prescriptions().size() + " prescriptions", false, nextAction, sortAt);
    }

    public record TodayAgendaView(String timeZone, LocalDate localDate, ActivePlanView activePlan,
                                  List<AgendaSessionView> sessions, String state) {
        public TodayAgendaView { sessions = List.copyOf(sessions); }
    }

    public record ActivePlanView(UUID planId, UUID revisionId, int revisionNumber) { }

    public record AgendaSessionView(UUID sessionId, String title, int expectedDurationMinutes,
                                    LocalDate scheduledDate, Instant availableFrom, Instant availableTo,
                                    String status, String doseSummary, boolean safetyBlocked,
                                    String nextAction, Instant sortAt) { }
}
