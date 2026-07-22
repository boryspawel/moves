package com.motionecosystem.notification.reminders;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReminderDeliveryServiceTest {
    private final Deliveries deliveries = new Deliveries();
    private final ReminderDeliveryService service = new ReminderDeliveryService(deliveries.repository(), (actor, event, aggregate, id) -> { },
            Clock.fixed(Instant.parse("2025-01-08T10:00:00Z"), ZoneOffset.UTC));

    @Test void derivesWeeklyFrequencyFromDurableDeliveredAuditRatherThanCallerContext() {
        UUID participant = UUID.randomUUID();
        deliveries.deliveredThisWeek = 3L;

        var result = service.evaluateAndRecord(participant, UUID.randomUUID(), "IN_APP", context(0), "weekly-count");

        assertThat(result).extracting(ReminderDeliveryService.DeliveryResult::delivered,
                ReminderDeliveryService.DeliveryResult::reasonCode).containsExactly(false, "MAX_FREQUENCY_REACHED");
        assertThat(deliveries.countParticipant).isEqualTo(participant);
        assertThat(deliveries.countStart).isEqualTo(LocalDate.of(2025, 1, 6));
        assertThat(deliveries.countEnd).isEqualTo(LocalDate.of(2025, 1, 12));
    }

    @Test void replaysSemanticDuplicateWhenIdempotencyKeyDiffers() {
        UUID participant = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        ReminderDelivery existing = new ReminderDelivery(participant, session, "SESSION_REMINDER_V1", ReminderPolicyEvaluator.RULE_VERSION,
                LocalDate.of(2025, 1, 8), "IN_APP", "original-key", true, Instant.parse("2025-01-08T10:00:00Z"));
        deliveries.semanticReplay = Optional.of(existing);

        var result = service.evaluateAndRecord(participant, session, "IN_APP", context(0), "different-key");

        assertThat(result).extracting(ReminderDeliveryService.DeliveryResult::id,
                ReminderDeliveryService.DeliveryResult::delivered).containsExactly(existing.id, true);
        assertThat(deliveries.saveCalls).isZero();
    }

    private static ReminderPolicyEvaluator.Context context(int callerMessagesThisWeek) {
        return new ReminderPolicyEvaluator.Context(Instant.parse("2025-01-08T10:00:00Z"), ZoneId.of("UTC"),
                LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(22, 0), LocalTime.of(7, 0), true, false,
                3, callerMessagesThisWeek, false, false, false, true, false, false, false);
    }

    private static final class Deliveries {
        private long deliveredThisWeek;
        private Optional<ReminderDelivery> semanticReplay = Optional.empty();
        private UUID countParticipant;
        private LocalDate countStart;
        private LocalDate countEnd;
        private int saveCalls;

        ReminderDeliveryRepository repository() {
            return (ReminderDeliveryRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { ReminderDeliveryRepository.class }, (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdempotencyKey" -> Optional.empty();
                        case "countByParticipantAccountIdAndDecisionAndLocalDeliveryDateBetween" -> {
                            countParticipant = (UUID) args[0];
                            countStart = (LocalDate) args[2];
                            countEnd = (LocalDate) args[3];
                            yield deliveredThisWeek;
                        }
                        case "findByParticipantAccountIdAndPlannedSessionIdAndReasonCodeAndLocalDeliveryDate" -> semanticReplay;
                        case "saveAndFlush" -> {
                            saveCalls++;
                            yield args[0];
                        }
                        case "toString" -> "ReminderDeliveryRepository test double";
                        default -> throw new UnsupportedOperationException(method.toString());
                    });
        }
    }
}
