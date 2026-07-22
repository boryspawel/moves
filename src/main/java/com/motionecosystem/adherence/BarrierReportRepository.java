package com.motionecosystem.adherence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BarrierReportRepository extends JpaRepository<BarrierReport, UUID> {
    Optional<BarrierReport> findByParticipantAccountIdAndIdempotencyKey(UUID participantAccountId, String idempotencyKey);
}
