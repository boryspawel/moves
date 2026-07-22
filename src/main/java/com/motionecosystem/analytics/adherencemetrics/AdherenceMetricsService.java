package com.motionecosystem.analytics.adherencemetrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal, neutral and retention-bounded adherence instrumentation. */
@Service
@RequiredArgsConstructor
public class AdherenceMetricsService {
    private static final int EXPERIMENT_VERSION = 1;
    private static final List<Experiment> EXPERIMENTS = List.of(
            new Experiment("REMINDER_PRESENTATION", List.of("CLASSIC", "BARRIER_FIRST")),
            new Experiment("AGENDA_PRESENTATION", List.of("DASHBOARD", "TODAY_ONLY")),
            new Experiment("AVAILABILITY_PLAN", List.of("MINIMUM_PLAN", "STANDARD_PLAN")));
    private final AdherenceExperimentAssignmentRepository assignments;
    private final AdherenceMetricEventRepository events;
    private final Clock clock;

    @Transactional
    public void ensureAssignments(UUID participant) {
        Instant now = clock.instant();
        for (Experiment experiment : EXPERIMENTS) {
            if (assignments.findByParticipantAccountIdAndExperimentKeyAndExperimentVersion(
                    participant, experiment.key(), EXPERIMENT_VERSION).isEmpty()) {
                String variant = experiment.variants().get(Math.floorMod(
                        bucket(participant, experiment.key()), experiment.variants().size()));
                try {
                    assignments.saveAndFlush(new AdherenceExperimentAssignment(participant, experiment.key(),
                            EXPERIMENT_VERSION, variant, now));
                } catch (DataIntegrityViolationException ignored) { /* concurrent first assignment is equivalent */ }
            }
        }
    }

    @Transactional
    public void record(UUID participant, String eventCode, UUID reference, UUID planRevisionId,
                       UUID plannedSessionId, UUID sessionAttemptId, String ruleVersionCode, String variantCode) {
        String key = eventCode + ":" + reference;
        if (events.existsByDeduplicationKey(key)) return;
        Instant now = clock.instant();
        try {
            events.saveAndFlush(new AdherenceMetricEvent(participant, eventCode, reference, planRevisionId,
                    plannedSessionId, sessionAttemptId, ruleVersionCode, variantCode, key, now,
                    now.plus(180, ChronoUnit.DAYS)));
        } catch (DataIntegrityViolationException ignored) { /* idempotent duplicate */ }
    }

    /** Idempotent retention operation invoked daily by the internal cleanup job. */
    @Transactional
    public long purgeExpired() { return events.deleteByExpiresAtBefore(clock.instant()); }

    private static int bucket(UUID participant, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((participant + ":" + key + ":" + EXPERIMENT_VERSION)
                    .getBytes(StandardCharsets.UTF_8));
            return Integer.parseUnsignedInt(HexFormat.of().formatHex(digest, 0, 4), 16);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Experiment(String key, List<String> variants) { }
}
