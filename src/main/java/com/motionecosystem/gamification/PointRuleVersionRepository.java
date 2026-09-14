package com.motionecosystem.gamification;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

interface PointRuleVersionRepository extends JpaRepository<PointRuleVersion, UUID> {
    Optional<PointRuleVersion> findByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PointRuleVersion r where r.active = true")
    Optional<PointRuleVersion> findActiveForUpdate();
}
