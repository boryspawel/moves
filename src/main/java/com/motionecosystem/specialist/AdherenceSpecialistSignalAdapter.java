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

    @Override
    @Transactional
    public void signalRecoveryContact(UUID participantAccountId, UUID recoveryEpisodeId) {
        if (signals.existsByRecoveryEpisodeId(recoveryEpisodeId)) return;
        try {
            signals.saveAndFlush(new AdherenceContactSignal(participantAccountId, null, recoveryEpisodeId,
                    "RECOVERY_EPISODE", "ROUTINE", clock.instant()));
        } catch (DataIntegrityViolationException ignored) {
            // The unique recovery reference makes concurrent retries safely idempotent.
        }
    }

    @Override
    public void signalWorklist(AdherenceSpecialistSignalPort.WorklistSignal signal) {
        // Worklist projection is owned by the specialist module; this adapter is its adherence boundary.
        worklist.signal(signal);
    }

    private final SpecialistWorklistService worklist;
}

interface AdherenceContactSignalRepository extends JpaRepository<AdherenceContactSignal, UUID> {
    boolean existsByBarrierReportId(UUID barrierReportId);
    boolean existsByRecoveryEpisodeId(UUID recoveryEpisodeId);
}

@Entity
@Table(name = "adherence_contact_signal", schema = "specialist")
class AdherenceContactSignal {
    @Id UUID id;
    UUID participantAccountId;
    UUID barrierReportId;
    UUID recoveryEpisodeId;
    String category;
    String priority;
    String status;
    Instant createdAt;
    protected AdherenceContactSignal() { }
    AdherenceContactSignal(UUID participantAccountId, UUID barrierReportId, String category, String priority, Instant createdAt) {
        this(participantAccountId, barrierReportId, null, category, priority, createdAt);
    }
    AdherenceContactSignal(UUID participantAccountId, UUID barrierReportId, UUID recoveryEpisodeId, String category, String priority, Instant createdAt) {
        this.id = UUID.randomUUID(); this.participantAccountId = participantAccountId; this.barrierReportId = barrierReportId;
        this.recoveryEpisodeId = recoveryEpisodeId;
        this.category = category; this.priority = priority; this.status = "OPEN"; this.createdAt = createdAt;
    }
}
