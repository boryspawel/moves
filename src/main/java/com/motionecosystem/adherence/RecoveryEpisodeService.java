package com.motionecosystem.adherence;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import com.motionecosystem.specialist.api.AdherenceSpecialistSignalPort;
import com.motionecosystem.trainingexecution.api.SessionStartAuthorizationPort;
import com.motionecosystem.trainingexecution.SessionExecutionAttemptService;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import com.motionecosystem.trainingplanning.api.ParticipantPlanWindowHistoryQueryPort;
import com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort;
import java.time.temporal.ChronoUnit;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Versioned recovery aggregate and its participant projection; it never changes a plan or safety envelope. */
@Service
@RequiredArgsConstructor
public class RecoveryEpisodeService implements SessionStartAuthorizationPort {
    private static final List<String> ACTIVE = List.of("OPEN", "RETURN_IN_PROGRESS");
    private final CurrentAccountService accounts;
    private final ParticipantContextQueryPort participants;
    private final PlanRevisionQueryPort revisions;
    private final SessionSafetyDecisionQueryPort safety;
    private final ParticipantPlanWindowHistoryQueryPort windows;
    private final ParticipantExecutionHistoryQueryPort history;
    private final RecoveryEpisodeRepository episodes;
    private final RecoveryOfferRepository offers;
    private final RecoveryOfferOptionRepository offerOptions;
    private final RecoveryChoiceRepository choices;
    private final ObjectProvider<SessionExecutionAttemptService> attempts;
    private final AdherenceSpecialistSignalPort specialistSignals;
    private final AdherenceMetricsService metrics;
    private final Clock clock;

    /** Called after a persisted barrier; only illness and symptom reports open a recovery episode immediately. */
    @Transactional
    void detectFromBarrier(BarrierReport report) {
        if (!"ILLNESS".equals(report.category) && !"PAIN_OR_SYMPTOMS".equals(report.category)) return;
        if (episodes.findFirstByParticipantAccountIdAndStatusInOrderByOpenedAtDesc(report.participantAccountId, ACTIVE).isPresent()) return;
        var context = participants.findContext(report.participantAccountId).orElse(null);
        if (context == null) return;
        try {
            episodes.saveAndFlush(new RecoveryEpisode(report.participantAccountId, context.timeZone().getId(),
                    clock.instant().atZone(context.timeZone()).toLocalDate(), report.planRevisionId,
                    "BARRIER_" + report.category, report.category, report.id, 0, 0, clock.instant()));
        } catch (DataIntegrityViolationException ignored) {
            // Partial unique index is the concurrency boundary for detection.
        }
        episodes.findFirstByParticipantAccountIdAndStatusInOrderByOpenedAtDesc(report.participantAccountId, ACTIVE)
                .ifPresent(this::ensureOffer);
    }

    /** Idempotent evaluation used by lifecycle hooks and the bounded background scan. */
    @Transactional
    public void detect(UUID participant) {
        if (episodes.findFirstByParticipantAccountIdAndStatusInOrderByOpenedAtDesc(participant, ACTIVE).isPresent()) return;
        var context = participants.findContext(participant).orElse(null);
        var revision = revisions.findActiveRevision(participant).orElse(null);
        if (context == null || revision == null) return;
        Instant now = clock.instant();
        var starts = history.starts(participant, now.minus(28, ChronoUnit.DAYS), now.plusMillis(1), 64);
        Instant latest = starts.stream().map(ParticipantExecutionHistoryQueryPort.ExecutionStart::startedAt).max(Instant::compareTo).orElse(null);
        int gap = latest == null ? 0 : (int) ChronoUnit.DAYS.between(latest.atZone(context.timeZone()).toLocalDate(), now.atZone(context.timeZone()).toLocalDate());
        var ended = windows.completedWindows(participant, now, 32);
        long unstarted = ended.stream().filter(w -> starts.stream().noneMatch(s -> s.attemptId() != null && s.startedAt().isAfter(w.endedAt()))).count();
        boolean regularity = starts.size() >= 3 && latest != null && gap >= 3;
        if (gap < 3 && unstarted < 2 && !regularity) return;
        String trigger = unstarted >= 2 ? "MISSED_WINDOWS" : regularity ? "REGULARITY_GAP" : "NO_START_DAYS";
        RecoveryEpisode episode = new RecoveryEpisode(participant, context.timeZone().getId(), now.atZone(context.timeZone()).toLocalDate(),
                revision.revisionId(), trigger, null, null, gap, (int) unstarted, now);
        try { episodes.saveAndFlush(episode); ensureOffer(episode); } catch (DataIntegrityViolationException ignored) { }
    }

    @Transactional(readOnly = true)
    public RecoveryView current(String subject) { return view(participant(subject), false); }

    @Transactional
    public RecoveryView choose(String subject, UUID episodeId, UUID offerId, long aggregateVersion, String path, String key) {
        UUID participant = participant(subject);
        RecoveryEpisode episode = episodes.findByIdAndParticipantAccountId(episodeId, participant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "recovery episode not found"));
        if (!episode.active()) throw new ResponseStatusException(HttpStatus.CONFLICT, "recovery episode is closed");
        if (key == null || key.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        var replay = choices.findByRecoveryEpisodeIdAndIdempotencyKey(episode.id, key.trim());
        if (replay.isPresent()) return view(participant, true);
        if (episode.version != aggregateVersion) throw stale(participant);
        RecoveryOffer offer = offers.findById(offerId).filter(item -> item.recoveryEpisodeId.equals(episode.id))
                .orElseThrow(() -> stale(participant));
        var active = revisions.findActiveRevision(participant).orElseThrow(() -> stale(participant));
        if (!offer.planRevisionId.equals(active.revisionId()) || offer.staleAt != null) throw stale(participant);
        List<String> paths = offerOptions.findByRecoveryOfferIdOrderByOrdinal(offer.id).stream().map(item -> item.path).toList();
        if (path == null || !paths.contains(path)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recovery path is not offered");
        UUID target = targetSession(active);
        episode.select(path, target, clock.instant());
        choices.save(new RecoveryChoice(episode.id, offer.id, path, key.trim(), clock.instant()));
        metrics.record(participant, "RECOVERY_CHOICE_SELECTED", episode.id, active.revisionId(), target, null,
                episode.policyVersionCode, path);
        if ("CONTACT_SPECIALIST".equals(path)) specialistSignals.signalRecoveryContact(participant, episode.id);
        if ("START_MINIMUM".equals(path) || "START_SHORT".equals(path)) {
            String variant = path.substring("START_".length());
            var attempt = attempts.getObject().start(subject, target, active.revisionId(), variant, key.trim());
            var zone = participants.findContext(participant).map(item -> item.timeZone()).orElse(java.time.ZoneOffset.UTC);
            episode.started(attempt.attemptId(), clock.instant().atZone(zone).toLocalDate(), clock.instant());
        }
        return view(participant, false);
    }

    @Override
    @Transactional(readOnly = true)
    public void authorize(UUID participant, UUID sessionId, String variant) {
        var active = episodes.findFirstByParticipantAccountIdAndStatusInOrderByOpenedAtDesc(participant, ACTIVE);
        if (active.isEmpty()) return;
        RecoveryEpisode episode = active.get();
        if ("STANDARD".equals(variant)) throw new ResponseStatusException(HttpStatus.CONFLICT, "recovery episode does not permit STANDARD");
        if (episode.targetPlannedSessionId == null || !episode.targetPlannedSessionId.equals(sessionId)
                || !("START_" + variant).equals(episode.selectedPath)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "select an offered recovery path before starting a session");
        }
        var revision = revisions.findActiveRevision(participant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "active plan is unavailable"));
        var safetyDecision = safety.evaluateForSessions(participant, revision.revisionId(), List.of(sessionId), clock.instant()).get(sessionId);
        if (safetyDecision == null || safetyDecision.status() != SessionSafetyDecisionQueryPort.SafetyDecisionStatus.ALLOWED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "recovery start requires a current allowed safety assessment");
        }
    }

    @Transactional
    public void attemptStarted(UUID participant, UUID attemptId, UUID sessionId) {
        episodes.findFirstByParticipantAccountIdAndTargetPlannedSessionIdAndStatusIn(participant, sessionId, ACTIVE)
                .ifPresent(episode -> {
                    var zone = participants.findContext(participant).map(item -> item.timeZone()).orElse(java.time.ZoneOffset.UTC);
                    episode.started(attemptId, clock.instant().atZone(zone).toLocalDate(), clock.instant());
                });
    }

    @Transactional
    public void executionCompleted(UUID participant, UUID sessionId, UUID executionId) {
        episodes.findFirstByParticipantAccountIdAndTargetPlannedSessionIdAndStatusIn(participant, sessionId, ACTIVE)
                .ifPresent(episode -> {
                    episode.resolved(executionId, clock.instant());
                    metrics.record(participant, "RECOVERY_RETURN_COMPLETED", episode.id, episode.planRevisionIdAtOpening,
                            sessionId, episode.returnAttemptId, episode.policyVersionCode, episode.selectedPath);
                });
    }

    private RecoveryView view(UUID participant, boolean createOffer) {
        var current = episodes.findFirstByParticipantAccountIdAndStatusInOrderByOpenedAtDesc(participant, ACTIVE);
        if (current.isEmpty()) return null;
        RecoveryEpisode episode = current.get();
        var active = revisions.findActiveRevision(participant).orElse(null);
        if (active == null) return new RecoveryView(episode.id, episode.status, "RECOVERY_PLAN_REQUIRED", episode.policyVersionCode,
                episode.openedAt, episode.gapDays, null, null, List.of(), episode.selectedPath, null, episode.version);
        UUID target = targetSession(active);
        var decision = safety.evaluateForSessions(participant, active.revisionId(), List.of(target), clock.instant()).get(target);
        String safetyState = decision == null ? "NOT_ASSESSED" : decision.status().name();
        RecoveryOffer offer = offers.findFirstByRecoveryEpisodeIdOrderByCreatedAtDesc(episode.id)
                .filter(item -> item.planRevisionId.equals(active.revisionId()) && item.staleAt == null && item.safetyState.equals(safetyState))
                .orElseGet(() -> createOffer ? createOffer(episode, active, target, safetyState) : null);
        List<String> paths = offer == null ? List.of() : offerOptions.findByRecoveryOfferIdOrderByOrdinal(offer.id).stream().map(item -> item.path).toList();
        return new RecoveryView(episode.id, episode.status, "RECOVERY_RETURN_AVAILABLE", episode.policyVersionCode,
                episode.openedAt, episode.gapDays, target, offer == null ? null : offer.id, paths,
                episode.selectedPath, episode.returnAttemptId == null ? null : "RETURN_IN_PROGRESS", episode.version);
    }

    private void ensureOffer(RecoveryEpisode episode) {
        var active = revisions.findActiveRevision(episode.participantAccountId).orElse(null);
        if (active == null) return;
        UUID target = targetSession(active);
        var decision = safety.evaluateForSessions(episode.participantAccountId, active.revisionId(), List.of(target), clock.instant()).get(target);
        String safetyState = decision == null ? "NOT_ASSESSED" : decision.status().name();
        offers.findFirstByRecoveryEpisodeIdOrderByCreatedAtDesc(episode.id)
                .filter(item -> item.planRevisionId.equals(active.revisionId()) && item.staleAt == null && item.safetyState.equals(safetyState))
                .orElseGet(() -> createOffer(episode, active, target, safetyState));
    }

    private RecoveryOffer createOffer(RecoveryEpisode episode, PlanRevisionQueryPort.PlanRevisionSnapshot revision, UUID target, String safetyState) {
        offers.findFirstByRecoveryEpisodeIdOrderByCreatedAtDesc(episode.id).filter(item -> item.staleAt == null)
                .ifPresent(item -> item.staleAt = clock.instant());
        RecoveryOffer offer = offers.save(new RecoveryOffer(episode.id, revision.revisionId(), safetyState, clock.instant()));
        List<String> paths = paths(episode, revision, target, safetyState);
        for (int i = 0; i < paths.size(); i++) offerOptions.save(new RecoveryOfferOption(offer.id, i + 1, paths.get(i), i == 0));
        return offer;
    }
    private static List<String> paths(RecoveryEpisode episode, PlanRevisionQueryPort.PlanRevisionSnapshot revision, UUID target, String safetyState) {
        if ("BLOCKED".equals(safetyState) || "NOT_ASSESSED".equals(safetyState) || "REQUIRES_REVIEW".equals(safetyState)) return List.of("RESCHEDULE", "CONTACT_SPECIALIST");
        var session = revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream()).filter(s -> s.id().equals(target)).findFirst().orElseThrow();
        boolean shortAvailable = session.variants().stream().anyMatch(v -> "SHORT".equals(v.type()));
        boolean minimumAvailable = session.variants().stream().anyMatch(v -> "MINIMUM".equals(v.type()));
        List<String> paths = new ArrayList<>();
        if (minimumAvailable) paths.add("START_MINIMUM");
        if (episode.gapDays < 14 && shortAvailable) paths.add("START_SHORT");
        paths.add("RESCHEDULE"); paths.add("CONTACT_SPECIALIST");
        return List.copyOf(paths);
    }
    private static UUID targetSession(PlanRevisionQueryPort.PlanRevisionSnapshot revision) {
        return revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream())
                .map(PlanRevisionQueryPort.SessionSnapshot::id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "active plan has no session"));
    }
    private UUID participant(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        return account.id();
    }
    private ResponseStatusException stale(UUID participant) { return new ResponseStatusException(HttpStatus.CONFLICT, "RECOVERY_OFFER_STALE"); }
    public record RecoveryView(UUID episodeId, String state, String messageCode, String policyVersion, Instant openedAt,
            int gapDays, UUID targetSessionId, UUID offerId, List<String> options, String selectedPath, String returnState, long version) {
        public RecoveryView { options = List.copyOf(options); }
    }
}
