package com.motionecosystem.adherence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecoveryChoiceRepository extends JpaRepository<RecoveryChoice, UUID> {
    Optional<RecoveryChoice> findByRecoveryEpisodeIdAndIdempotencyKey(UUID episodeId, String idempotencyKey);
}
