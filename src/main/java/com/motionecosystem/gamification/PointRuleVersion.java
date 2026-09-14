package com.motionecosystem.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_rule_version", schema = "gamification")
class PointRuleVersion {
    @Id private UUID id;
    @Column(name = "version_name", nullable = false, length = 80) private String versionName;
    @Column(name = "base_points", nullable = false) private int basePoints;
    @Column(name = "daily_limit", nullable = false) private int dailyLimit;
    @Column(name = "weekly_limit", nullable = false) private int weeklyLimit;
    @Column(name = "cooldown_seconds", nullable = false) private int cooldownSeconds;
    @Column(name = "repeat_window_days", nullable = false) private int repeatWindowDays;
    @Column(name = "full_reward_occurrences", nullable = false) private int fullRewardOccurrences;
    @Column(name = "reduced_reward_percent", nullable = false) private int reducedRewardPercent;
    @Column(nullable = false) private boolean active;
    @Column(name = "published_by_subject", nullable = false) private String publishedBySubject;
    @Column(name = "published_at", nullable = false) private Instant publishedAt;

    protected PointRuleVersion() {
    }

    PointRuleVersion(UUID id, String versionName, int basePoints, int dailyLimit, int weeklyLimit,
                     int cooldownSeconds, int repeatWindowDays, int fullRewardOccurrences,
                     int reducedRewardPercent, String publishedBySubject, Instant publishedAt) {
        this.id = id; this.versionName = versionName; this.basePoints = basePoints; this.dailyLimit = dailyLimit;
        this.weeklyLimit = weeklyLimit; this.cooldownSeconds = cooldownSeconds; this.repeatWindowDays = repeatWindowDays;
        this.fullRewardOccurrences = fullRewardOccurrences; this.reducedRewardPercent = reducedRewardPercent;
        this.active = true; this.publishedBySubject = publishedBySubject; this.publishedAt = publishedAt;
    }

    void deactivate() { active = false; }
    UUID id() { return id; }
    String versionName() { return versionName; }
    int basePoints() { return basePoints; }
    int dailyLimit() { return dailyLimit; }
    int weeklyLimit() { return weeklyLimit; }
    int cooldownSeconds() { return cooldownSeconds; }
    int repeatWindowDays() { return repeatWindowDays; }
    int fullRewardOccurrences() { return fullRewardOccurrences; }
    int reducedRewardPercent() { return reducedRewardPercent; }
}
