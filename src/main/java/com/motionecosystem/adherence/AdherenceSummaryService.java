package com.motionecosystem.adherence;

import com.motionecosystem.adherence.api.AdherenceSummary;
import com.motionecosystem.adherence.api.AdherenceSummaryQueryPort;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionProgressQueryPort.ExecutionState;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdherenceSummaryService implements AdherenceSummaryQueryPort {
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private final ParticipantContextQueryPort participants;
    private final PlanRevisionQueryPort revisions;
    private final SessionExecutionProgressQueryPort progress;
    private final Clock clock;

    public AdherenceSummaryService(ParticipantContextQueryPort participants, PlanRevisionQueryPort revisions,
            SessionExecutionProgressQueryPort progress, Clock clock) {
        this.participants = participants;
        this.revisions = revisions;
        this.progress = progress;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AdherenceSummary summarize(UUID participantId, LocalDate requestedFrom, LocalDate requestedTo) {
        if (participantId == null) throw bad("participantId is required");
        ZoneId zone = participants.findContextByParticipantId(participantId)
                .map(ParticipantContextQueryPort.ParticipantRecordContext::timeZone)
                .orElse(null);
        if (zone == null) return new AdherenceSummary("CURRENT_ACTIVE_PLANS", "TIME_ZONE_REQUIRED", null,
                null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        Period period = period(zone, requestedFrom, requestedTo);
        var activeRevisions = revisions.findActiveRevisions(participantId);
        List<PlanRevisionQueryPort.SessionSnapshot> activeSessions = activeRevisions.stream()
                .flatMap(revision -> revision.cycles().stream())
                .flatMap(cycle -> cycle.microcycles().stream())
                .flatMap(microcycle -> microcycle.sessions().stream())
                .toList();
        int undated = (int) activeSessions.stream().filter(session -> localDate(session, zone) == null).count();
        List<PlanRevisionQueryPort.SessionSnapshot> selected = activeSessions.stream()
                .filter(session -> inPeriod(localDate(session, zone), period)).toList();
        Map<UUID, SessionExecutionProgressQueryPort.SessionExecutionProgress> states = progress.findForSessions(participantId,
                selected.stream().map(PlanRevisionQueryPort.SessionSnapshot::id).toList());
        Counts counts = selected.stream().map(session -> states.get(session.id())).map(item -> item == null
                ? ExecutionState.NOT_STARTED : item.state()).collect(Counts::new, Counts::add, Counts::merge);
        int planned = selected.size();
        String status = activeRevisions.isEmpty() ? "NO_ACTIVE_PLAN" : planned == 0 ? "NO_SCHEDULED_SESSIONS" : "AVAILABLE";
        return new AdherenceSummary("CURRENT_ACTIVE_PLANS", status, zone.getId(),
                period.from(), period.to(), planned, undated, counts.notStarted, counts.inProgress, counts.paused,
                counts.completed, counts.partial, counts.skipped, counts.stopped, counts.abandoned,
                planned == 0 ? null : BigDecimal.valueOf(counts.completed * 100L)
                        .divide(BigDecimal.valueOf(planned), 2, RoundingMode.HALF_UP));
    }

    private Period period(ZoneId zone, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) throw bad("from and to must be provided together");
        LocalDate end = to == null ? LocalDate.now(clock.withZone(zone)) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end)) throw bad("from must not be after to");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw bad("period must not exceed 366 days");
        return new Period(start, end);
    }

    private static LocalDate localDate(PlanRevisionQueryPort.SessionSnapshot session, ZoneId zone) {
        if (session.scheduledDate() != null) return session.scheduledDate();
        return session.availableFrom() == null ? null : session.availableFrom().atZone(zone).toLocalDate();
    }

    private static boolean inPeriod(LocalDate date, Period period) {
        return date != null && !date.isBefore(period.from()) && !date.isAfter(period.to());
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record Period(LocalDate from, LocalDate to) { }

    private static final class Counts {
        private int notStarted;
        private int inProgress;
        private int paused;
        private int completed;
        private int partial;
        private int skipped;
        private int stopped;
        private int abandoned;

        private void add(ExecutionState state) {
            switch (state) {
                case NOT_STARTED -> notStarted++;
                case IN_PROGRESS -> inProgress++;
                case PAUSED -> paused++;
                case COMPLETED -> completed++;
                case PARTIAL -> partial++;
                case SKIPPED -> skipped++;
                case STOPPED -> stopped++;
                case ABANDONED -> abandoned++;
            }
        }

        private void merge(Counts other) {
            notStarted += other.notStarted; inProgress += other.inProgress; paused += other.paused;
            completed += other.completed; partial += other.partial; skipped += other.skipped;
            stopped += other.stopped; abandoned += other.abandoned;
        }
    }
}
