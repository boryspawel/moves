package com.motionecosystem.adherence;

import com.motionecosystem.adherence.BarrierReportController.BarrierReportCommand;
import com.motionecosystem.analytics.adherencemetrics.AdherenceMetricsService;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.safety.api.SessionSafetyDecisionQueryPort;
import com.motionecosystem.specialist.api.AdherenceSpecialistSignalPort;
import com.motionecosystem.trainingexecution.api.SessionExecutionAttemptQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Deterministic, non-clinical routing for participant-reported barriers. */
@Service
@RequiredArgsConstructor
public class BarrierReportService {
    private static final Set<String> CATEGORIES = Set.of("NO_TIME", "PAIN_OR_SYMPTOMS", "TOO_DIFFICULT",
            "UNSURE_TECHNIQUE", "FATIGUE", "ILLNESS", "LOGISTICS", "LOW_MOTIVATION", "OTHER");
    private final CurrentAccountService accounts;
    private final PlanRevisionQueryPort revisions;
    private final SessionExecutionAttemptQueryPort attempts;
    private final SessionSafetyDecisionQueryPort safety;
    private final BarrierReportRepository reports;
    private final AdherenceSpecialistSignalPort specialistSignals;
    private final RecoveryEpisodeService recovery;
    private final AdherenceMetricsService metrics;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public BarrierReportView report(String subject, BarrierReportCommand command, String idempotencyKey) {
        UUID participant = participant(subject);
        String key = key(idempotencyKey);
        var replay = reports.findByParticipantAccountIdAndIdempotencyKey(participant, key);
        if (replay.isPresent()) return view(replay.get());
        if (command == null || command.plannedSessionId() == null) bad("plannedSessionId is required");
        String category = category(command.category());
        Context context = context(participant, command);
        List<String> options = options(category, context);
        String selected = command.selectedAction() == null || command.selectedAction().isBlank()
                ? null : command.selectedAction().trim();
        if (selected != null && !options.contains(selected)) bad("selectedAction is not offered by the applicable rule");
        String outcome = outcome(selected, context);
        BarrierReport created = new BarrierReport(participant, command.plannedSessionId(), command.sessionAttemptId(),
                context.revisionId(), category, String.join(",", options), selected, outcome, key, clock.instant());
        try {
            reports.saveAndFlush(created);
        } catch (DataIntegrityViolationException exception) {
            return reports.findByParticipantAccountIdAndIdempotencyKey(participant, key)
                    .map(BarrierReportService::view).orElseThrow(() -> exception);
        }
        recovery.detectFromBarrier(created);
        metrics.record(participant, "BARRIER_REPORTED", created.id, context.revisionId(), command.plannedSessionId(),
                command.sessionAttemptId(), created.ruleVersionCode, selected);
        long categoryCount = reports.countByParticipantAccountIdAndCategory(participant, category);
        if (categoryCount >= 2) {
            specialistSignals.signalWorklist(new AdherenceSpecialistSignalPort.WorklistSignal(participant,
                    context.revisionId(), "REPEATED_BARRIERS", "MEDIUM", "BARRIER_PATTERN_" + category,
                    created.ruleVersionCode));
        }
        if ("PAIN_OR_SYMPTOMS".equals(category) || "UNSURE_TECHNIQUE".equals(category)
                || "TOO_DIFFICULT".equals(category)) {
            String worklistCategory = "PAIN_OR_SYMPTOMS".equals(category) ? "ESCALATING_SYMPTOMS"
                    : "UNSURE_TECHNIQUE".equals(category) ? "TECHNIQUE_UNCERTAINTY" : "PLAN_MISMATCH";
            specialistSignals.signalWorklist(new AdherenceSpecialistSignalPort.WorklistSignal(participant,
                    context.revisionId(), worklistCategory,
                    "PAIN_OR_SYMPTOMS".equals(category) ? "HIGH" : "MEDIUM", category,
                    created.ruleVersionCode));
        }
        if ("CONTACT_SPECIALIST".equals(selected)) {
            specialistSignals.signalContact(participant, created.id, category,
                    "PAIN_OR_SYMPTOMS".equals(category) || "UNSURE_TECHNIQUE".equals(category));
        }
        audit.record(subject, "BARRIER_REPORTED", "BarrierReport", created.id);
        return view(created);
    }

    private Context context(UUID participant, BarrierReportCommand command) {
        UUID revisionId;
        boolean activeAttempt = false;
        if (command.sessionAttemptId() != null) {
            var attempt = attempts.findOwnedAttempt(participant, command.sessionAttemptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session attempt not found"));
            if (!attempt.plannedSessionId().equals(command.plannedSessionId())) bad("attempt belongs to another session");
            revisionId = attempt.planRevisionId(); activeAttempt = attempt.active();
        } else {
            revisionId = revisions.findActiveRevision(participant).map(PlanRevisionQueryPort.PlanRevisionSnapshot::revisionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "no active plan revision"));
        }
        var revision = revisions.findRevision(revisionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "plan revision is unavailable"));
        var session = revision.cycles().stream().flatMap(c -> c.microcycles().stream()).flatMap(m -> m.sessions().stream())
                .filter(item -> item.id().equals(command.plannedSessionId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "planned session not found"));
        var decision = safety.evaluateForSessions(participant, revisionId, List.of(session.id()), clock.instant()).get(session.id());
        boolean usable = decision != null && (decision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.ALLOWED
                || decision.status() == SessionSafetyDecisionQueryPort.SafetyDecisionStatus.REQUIRES_REVIEW);
        return new Context(revisionId, activeAttempt, usable,
                session.variants().stream().anyMatch(v -> "SHORT".equals(v.type())),
                session.variants().stream().anyMatch(v -> "MINIMUM".equals(v.type())));
    }

    private static List<String> options(String category, Context context) {
        if (!context.safetyAllowsExecution()) return List.of("CONTACT_SPECIALIST");
        List<String> result = new ArrayList<>();
        switch (category) {
            case "NO_TIME" -> { variantOptions(result, context); result.add("RESCHEDULE"); result.add("CONTACT_SPECIALIST"); }
            case "FATIGUE" -> { variantOptions(result, context); result.add("RESCHEDULE"); result.add("CONTACT_SPECIALIST"); }
            case "TOO_DIFFICULT" -> { if (context.minimumAvailable()) result.add("START_MINIMUM"); result.add("CONTACT_SPECIALIST"); }
            case "UNSURE_TECHNIQUE" -> { if (context.activeAttempt()) result.add("STOP_EXERCISE"); result.add("CONTACT_SPECIALIST"); }
            case "PAIN_OR_SYMPTOMS" -> { if (context.activeAttempt()) result.add("PAUSE_ATTEMPT"); result.add("CONTACT_SPECIALIST"); }
            case "ILLNESS" -> { if (context.activeAttempt()) result.add("PAUSE_ATTEMPT"); result.add("RESCHEDULE"); result.add("CONTACT_SPECIALIST"); }
            case "LOW_MOTIVATION" -> { if (context.minimumAvailable()) result.add("START_MINIMUM"); result.add("RESCHEDULE"); result.add("ABANDON_FOR_TODAY"); }
            case "LOGISTICS", "OTHER" -> { result.add("RESCHEDULE"); result.add("CONTACT_SPECIALIST"); }
            default -> throw new IllegalArgumentException(category);
        }
        return List.copyOf(result);
    }
    private static void variantOptions(List<String> options, Context context) {
        if (context.shortAvailable()) options.add("START_SHORT");
        if (context.minimumAvailable()) options.add("START_MINIMUM");
    }
    private static String outcome(String action, Context context) {
        if (action == null) return "OPTIONS_PRESENTED";
        return switch (action) {
            case "START_SHORT", "START_MINIMUM" -> context.activeAttempt() ? "REQUIRES_NEW_ATTEMPT" : "VARIANT_READY";
            case "PAUSE_ATTEMPT" -> "PAUSE_REQUESTED";
            case "STOP_EXERCISE" -> "EXERCISE_STOP_REQUESTED";
            case "RESCHEDULE" -> "RESCHEDULE_REQUESTED";
            case "ABANDON_FOR_TODAY" -> "DECLINED_WITHOUT_PENALTY";
            case "CONTACT_SPECIALIST" -> "CONTACT_SIGNAL_CREATED";
            default -> throw new IllegalArgumentException(action);
        };
    }
    private UUID participant(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "participant profile is required");
        return account.id();
    }
    private static String category(String value) { if (value == null || !CATEGORIES.contains(value.trim())) bad("barrier category is invalid"); return value.trim(); }
    private static String key(String value) { if (value == null || value.isBlank() || value.trim().length() > 120) bad("Idempotency-Key is required"); return value.trim(); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static BarrierReportView view(BarrierReport report) {
        return new BarrierReportView(report.id, report.plannedSessionId, report.sessionAttemptId, report.category,
                List.of(report.proposedOptions.split(",")), report.selectedAction, report.actionOutcome,
                report.ruleVersionCode, report.reportedAt);
    }
    private record Context(UUID revisionId, boolean activeAttempt, boolean safetyAllowsExecution,
                           boolean shortAvailable, boolean minimumAvailable) { }
    public record BarrierReportView(UUID id, UUID plannedSessionId, UUID sessionAttemptId, String category,
                                    List<String> proposedOptions, String selectedAction, String actionOutcome,
                                    String ruleVersion, java.time.Instant reportedAt) { }
}
