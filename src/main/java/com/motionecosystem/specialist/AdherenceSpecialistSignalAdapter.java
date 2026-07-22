package com.motionecosystem.specialist;

import com.motionecosystem.specialist.api.AdherenceSpecialistSignalPort;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class AdherenceSpecialistSignalAdapter implements AdherenceSpecialistSignalPort {
    private final AdherenceContactSignalRepository signals;
    private final Clock clock;

    @Override
    @Transactional
    public void signalContact(UUID participantAccountId, UUID barrierReportId, String category, boolean prompt) {
        if (signals.existsByBarrierReportId(barrierReportId)) return;
        try {
            signals.saveAndFlush(new AdherenceContactSignal(participantAccountId, barrierReportId, category,
                    prompt ? "PROMPT" : "ROUTINE", clock.instant()));
        } catch (DataIntegrityViolationException ignored) {
            // The unique barrier reference makes concurrent retries safely idempotent.
        }
    }
}

interface AdherenceContactSignalRepository extends JpaRepository<AdherenceContactSignal, UUID> {
    boolean existsByBarrierReportId(UUID barrierReportId);
}

@Entity
@Table(name = "adherence_contact_signal", schema = "specialist")
class AdherenceContactSignal {
    @Id UUID id;
    UUID participantAccountId;
    UUID barrierReportId;
    String category;
    String priority;
    String status;
    Instant createdAt;
    protected AdherenceContactSignal() { }
    AdherenceContactSignal(UUID participantAccountId, UUID barrierReportId, String category, String priority, Instant createdAt) {
        this.id = UUID.randomUUID(); this.participantAccountId = participantAccountId; this.barrierReportId = barrierReportId;
        this.category = category; this.priority = priority; this.status = "OPEN"; this.createdAt = createdAt;
    }
}
