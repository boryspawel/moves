package com.motionecosystem.adherence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_offer", schema = "adherence")
class RecoveryOffer {
    @Id UUID id;
    UUID recoveryEpisodeId;
    UUID planRevisionId;
    String safetyState;
    String policyVersionCode;
    Instant createdAt;
    Instant staleAt;
    protected RecoveryOffer() { }
    RecoveryOffer(UUID episodeId, UUID revisionId, String safetyState, Instant now) {
        id = UUID.randomUUID(); recoveryEpisodeId = episodeId; planRevisionId = revisionId;
        this.safetyState = safetyState; policyVersionCode = "RECOVERY_RETURN_V1"; createdAt = now;
    }
}
