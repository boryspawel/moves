package com.motionecosystem.notification.reminders;

import com.motionecosystem.audit.AuditRecorder;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistence boundary for an in-app delivery; uniqueness makes retries safe. */
@Service
@RequiredArgsConstructor
public class ReminderDeliveryService {
    private final ReminderDeliveryRepository deliveries;
    private final ReminderPolicyEvaluator policy = new ReminderPolicyEvaluator();
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public DeliveryResult evaluateAndRecord(UUID participant, UUID plannedSession, String channel,
                                            ReminderPolicyEvaluator.Context input, String idempotencyKey) {
        var replay = deliveries.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) return result(replay.get());
        LocalDate localDate = input.now().atZone(input.timeZone()).toLocalDate();
        LocalDate weekStart = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        int deliveredThisWeek = (int) Math.min(Integer.MAX_VALUE,
                deliveries.countByParticipantAccountIdAndDecisionAndLocalDeliveryDateBetween(participant, "DELIVERED", weekStart, weekEnd));
        var decisionInput = new ReminderPolicyEvaluator.Context(input.now(), input.timeZone(), input.preferredWindowStart(), input.preferredWindowEnd(),
                input.quietHoursStart(), input.quietHoursEnd(), input.remindersEnabled(), input.muted(), input.maxMessagesPerWeek(), deliveredThisWeek,
                input.completed(), input.rescheduled(), input.painOrSymptomsReported(), input.normalReminder(), input.returnReminder(), input.gentleReturnConsent(),
                false);
        var decision = policy.evaluate(decisionInput);
        var semanticReplay = deliveries.findByParticipantAccountIdAndPlannedSessionIdAndReasonCodeAndLocalDeliveryDate(participant, plannedSession,
                decision.reasonCode(), localDate);
        if (semanticReplay.isPresent()) return result(semanticReplay.get());
        ReminderDelivery entry = new ReminderDelivery(participant, plannedSession, decision.reasonCode(), decision.ruleVersionCode(), localDate,
                channel, idempotencyKey, decision.deliver(), clock.instant());
        try { deliveries.saveAndFlush(entry); } catch (DataIntegrityViolationException duplicate) {
            return deliveries.findByIdempotencyKey(idempotencyKey).or(() -> deliveries
                    .findByParticipantAccountIdAndPlannedSessionIdAndReasonCodeAndLocalDeliveryDate(participant, plannedSession,
                            decision.reasonCode(), localDate))
                    .map(ReminderDeliveryService::result).orElseThrow(() -> duplicate);
        }
        audit.record("system", "REMINDER_" + entry.decision, "ReminderDelivery", entry.id);
        return result(entry);
    }
    @Transactional(readOnly = true)
    public long messagesPerActiveRecipient(UUID participant) {
        var start = clock.instant().atZone(java.time.ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toInstant();
        return deliveries.countByParticipantAccountIdAndDecisionAndDecidedAtGreaterThanEqual(participant, "DELIVERED", start);
    }
    private static DeliveryResult result(ReminderDelivery entry) { return new DeliveryResult(entry.id, "DELIVERED".equals(entry.decision), entry.reasonCode, entry.ruleVersionCode); }
    public record DeliveryResult(UUID id, boolean delivered, String reasonCode, String ruleVersionCode) { }
}
