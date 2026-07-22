package com.motionecosystem.adherence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_choice", schema = "adherence")
class RecoveryChoice {
    @Id UUID id;
    UUID recoveryEpisodeId;
    UUID recoveryOfferId;
    String path;
    String idempotencyKey;
    Instant chosenAt;
    protected RecoveryChoice() { }
    RecoveryChoice(UUID episodeId, UUID offerId, String path, String key, Instant now) {
        id = UUID.randomUUID(); recoveryEpisodeId = episodeId; recoveryOfferId = offerId; this.path = path;
        idempotencyKey = key; chosenAt = now;
    }
}
