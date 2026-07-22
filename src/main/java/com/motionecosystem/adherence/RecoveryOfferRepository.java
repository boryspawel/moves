package com.motionecosystem.adherence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecoveryOfferRepository extends JpaRepository<RecoveryOffer, UUID> {
    Optional<RecoveryOffer> findFirstByRecoveryEpisodeIdOrderByCreatedAtDesc(UUID recoveryEpisodeId);
}
interface RecoveryOfferOptionRepository extends JpaRepository<RecoveryOfferOption, UUID> {
    List<RecoveryOfferOption> findByRecoveryOfferIdOrderByOrdinal(UUID recoveryOfferId);
}
