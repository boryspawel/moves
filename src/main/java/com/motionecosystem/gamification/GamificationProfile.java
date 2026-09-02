package com.motionecosystem.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gamification_profile", schema = "gamification")
class GamificationProfile {
    @Id
    @Column(name = "account_id")
    private UUID accountId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(length = 80)
    private String pseudonym;
    @Column(name = "ranking_visible", nullable = false)
    private boolean rankingVisible;
    @Column(name = "enabled_at")
    private Instant enabledAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GamificationProfile() {
    }

    GamificationProfile(UUID accountId, boolean enabled, String pseudonym, boolean rankingVisible,
                         Instant enabledAt, Instant updatedAt) {
        this.accountId = accountId;
        update(enabled, pseudonym, rankingVisible, enabledAt, updatedAt);
    }

    void update(boolean enabled, String pseudonym, boolean rankingVisible, Instant enabledAt, Instant updatedAt) {
        this.enabled = enabled;
        this.pseudonym = pseudonym;
        this.rankingVisible = rankingVisible;
        this.enabledAt = enabledAt;
        this.updatedAt = updatedAt;
    }

    UUID accountId() { return accountId; }
    boolean enabled() { return enabled; }
    String pseudonym() { return pseudonym; }
    boolean rankingVisible() { return rankingVisible; }
    Instant enabledAt() { return enabledAt; }
    Instant updatedAt() { return updatedAt; }
}
