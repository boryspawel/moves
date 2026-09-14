package com.motionecosystem.gamification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

interface GamificationProfileRepository extends JpaRepository<GamificationProfile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GamificationProfile p where p.accountId = :accountId")
    Optional<GamificationProfile> findByAccountIdForUpdate(UUID accountId);

    java.util.List<GamificationProfile> findByEnabledTrueAndRankingVisibleTrueAndPseudonymIsNotNull();
}
