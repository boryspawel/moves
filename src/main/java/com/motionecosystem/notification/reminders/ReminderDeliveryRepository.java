package com.motionecosystem.notification.reminders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, UUID> {
    Optional<ReminderDelivery> findByIdempotencyKey(String idempotencyKey);
    Optional<ReminderDelivery> findByParticipantAccountIdAndPlannedSessionIdAndReasonCodeAndLocalDeliveryDate(UUID participant, UUID session, String reason, LocalDate date);
    long countByParticipantAccountIdAndDecisionAndLocalDeliveryDateBetween(UUID participant, String decision, LocalDate start, LocalDate end);
    long countByParticipantAccountIdAndDecisionAndDecidedAtGreaterThanEqual(UUID participant, String decision, Instant since);
}
