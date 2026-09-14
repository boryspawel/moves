package com.motionecosystem.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ranking_projection", schema = "gamification")
class RankingProjection {
    @Id @Column(name = "account_id") private UUID accountId;
    @Column(nullable = false, length = 80) private String pseudonym;
    @Column(nullable = false) private long points;
    @Column(name = "rebuilt_at", nullable = false) private Instant rebuiltAt;
    protected RankingProjection() { }
    RankingProjection(UUID accountId, String pseudonym, long points, Instant rebuiltAt) {
        this.accountId = accountId; this.pseudonym = pseudonym; this.points = points; this.rebuiltAt = rebuiltAt;
    }
    UUID accountId() { return accountId; }
    String pseudonym() { return pseudonym; }
    long points() { return points; }
}
