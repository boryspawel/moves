package com.motionecosystem.gamification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RankingProjectionRepository extends JpaRepository<RankingProjection, UUID> {
    @Query("select r from RankingProjection r order by r.points desc, lower(r.pseudonym), r.accountId")
    List<RankingProjection> findRanking(Pageable pageable);
}
