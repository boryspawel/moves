package com.motionecosystem.notification.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReminderPolicyEvaluatorTest {
    private final ReminderPolicyEvaluator evaluator = new ReminderPolicyEvaluator();

    @Test void usesParticipantTimeZoneAndStrictQuietHours() {
        var decision = evaluator.evaluate(context(Instant.parse("2025-01-01T21:30:00Z"), ZoneId.of("Europe/Warsaw"), false, false, false, false, false));
        assertThat(decision).extracting(ReminderPolicyEvaluator.Decision::deliver, ReminderPolicyEvaluator.Decision::reasonCode).containsExactly(false, "QUIET_HOURS");
    }
    @Test void suppressesCompletedAndRescheduledSessions() {
        assertThat(evaluator.evaluate(context(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.of("UTC"), true, false, false, false, false)).reasonCode()).isEqualTo("SESSION_COMPLETED");
        assertThat(evaluator.evaluate(context(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.of("UTC"), false, true, false, false, false)).reasonCode()).isEqualTo("SESSION_RESCHEDULED");
    }
    @Test void suppressesPainDuplicatesAndOptOut() {
        assertThat(evaluator.evaluate(context(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.of("UTC"), false, false, true, false, false)).reasonCode()).isEqualTo("PAIN_OR_SYMPTOMS_REPORTED");
        assertThat(evaluator.evaluate(context(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.of("UTC"), false, false, false, false, true)).reasonCode()).isEqualTo("DUPLICATE_SESSION_REASON_DATE");
    }
    @Test void requiresConsentForGentleReturn() {
        var context = new ReminderPolicyEvaluator.Context(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.of("UTC"), LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(22, 0), LocalTime.of(7, 0), true, false, 3, 0, false, false, false, false, true, false, false);
        assertThat(evaluator.evaluate(context).reasonCode()).isEqualTo("RETURN_CONSENT_REQUIRED");
    }
    private static ReminderPolicyEvaluator.Context context(Instant now, ZoneId zone, boolean completed, boolean rescheduled, boolean pain, boolean ignored, boolean duplicate) {
        return new ReminderPolicyEvaluator.Context(now, zone, LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(22, 0), LocalTime.of(7, 0), true, false, 3, 0, completed, rescheduled, pain, true, false, false, duplicate);
    }
}
