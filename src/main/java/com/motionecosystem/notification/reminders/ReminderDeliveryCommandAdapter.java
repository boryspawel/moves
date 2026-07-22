package com.motionecosystem.notification.reminders;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Runtime adapter for delivery producers; it does not select recipients or schedule reminders. */
@Component
@RequiredArgsConstructor
public class ReminderDeliveryCommandAdapter {
    private final ReminderDeliveryService deliveries;

    public ReminderDeliveryService.DeliveryResult evaluateAndRecord(UUID participant, UUID plannedSession, String channel,
                                                                     ReminderPolicyEvaluator.Context input, String idempotencyKey) {
        return deliveries.evaluateAndRecord(participant, plannedSession, channel, input, idempotencyKey);
    }
}
