package com.motionecosystem.safety;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "readiness_check_in", schema = "safety")
class ReadinessCheckIn {

    @Id
    UUID id;
    @Column(name = "account_id", nullable = false)
    UUID accountId;
    @Column(name = "pain_level", nullable = false)
    int painLevel;
    @Column(name = "readiness_level", nullable = false)
    int readinessLevel;
    @Column(name = "pain_area")
    String painArea;
    @Column(name = "recorded_at", nullable = false)
    Instant recordedAt;

    protected ReadinessCheckIn() {
    }

    ReadinessCheckIn(UUID accountId, int painLevel, int readinessLevel, String painArea, Instant now) {
        id = UUID.randomUUID();
        this.accountId = accountId;
        this.painLevel = painLevel;
        this.readinessLevel = readinessLevel;
        this.painArea = painArea;
        recordedAt = now;
    }
}
