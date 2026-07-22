package com.motionecosystem.notification.reminders;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/** Pure rules-first evaluator. It deliberately carries reason codes, never clinical message content. */
public final class ReminderPolicyEvaluator {
    public static final String RULE_VERSION = "REMINDER_RULES_V1";

    public Decision evaluate(Context context) {
        if (!context.remindersEnabled()) return suppressed("REMINDERS_DISABLED");
        if (context.muted()) return suppressed("MUTED");
        if (context.completed()) return suppressed("SESSION_COMPLETED");
        if (context.rescheduled()) return suppressed("SESSION_RESCHEDULED");
        if (context.normalReminder() && context.painOrSymptomsReported()) return suppressed("PAIN_OR_SYMPTOMS_REPORTED");
        if (context.returnReminder() && !context.gentleReturnConsent()) return suppressed("RETURN_CONSENT_REQUIRED");
        if (context.deliveredForSessionReasonDate()) return suppressed("DUPLICATE_SESSION_REASON_DATE");
        if (context.messagesThisWeek() >= context.maxMessagesPerWeek()) return suppressed("MAX_FREQUENCY_REACHED");
        LocalTime local = context.now().atZone(context.timeZone()).toLocalTime();
        if (inside(local, context.quietHoursStart(), context.quietHoursEnd())) return suppressed("QUIET_HOURS");
        if (!inside(local, context.preferredWindowStart(), context.preferredWindowEnd())) return suppressed("OUTSIDE_PREFERRED_WINDOW");
        return new Decision(true, context.returnReminder() ? "GENTLE_RETURN_V1" : "SESSION_REMINDER_V1", RULE_VERSION);
    }

    private static Decision suppressed(String code) { return new Decision(false, code, RULE_VERSION); }
    private static boolean inside(LocalTime time, LocalTime start, LocalTime end) {
        if (start == null || end == null) return false;
        if (start.equals(end)) return true;
        return start.isBefore(end) ? !time.isBefore(start) && !time.isAfter(end) : !time.isBefore(start) || !time.isAfter(end);
    }

    public record Context(Instant now, ZoneId timeZone, LocalTime preferredWindowStart, LocalTime preferredWindowEnd,
                          LocalTime quietHoursStart, LocalTime quietHoursEnd, boolean remindersEnabled, boolean muted,
                          int maxMessagesPerWeek, int messagesThisWeek, boolean completed, boolean rescheduled,
                          boolean painOrSymptomsReported, boolean normalReminder, boolean returnReminder,
                          boolean gentleReturnConsent, boolean deliveredForSessionReasonDate) { }
    public record Decision(boolean deliver, String reasonCode, String ruleVersionCode) { }
}
