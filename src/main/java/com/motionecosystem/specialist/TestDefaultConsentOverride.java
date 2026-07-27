package com.motionecosystem.specialist;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "test_default_consent_override", schema = "consent")
class TestDefaultConsentOverride {
    @Id UUID id;
    @Column(name = "participant_id", nullable = false) UUID participantId;
    @Column(name = "specialist_id", nullable = false) UUID specialistId;
    @Column(nullable = false) String purpose;
    @Column(nullable = false) String scopes;
    @Column(name = "decision_source", nullable = false) String decisionSource;
    @Column(name = "accepted_by_participant", nullable = false) boolean acceptedByParticipant;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "created_by", nullable = false) String createdBy;
    protected TestDefaultConsentOverride() { }
    TestDefaultConsentOverride(UUID participantId, UUID specialistId, String purpose, String scopes, String createdBy, Instant createdAt) {
        id = UUID.randomUUID(); this.participantId = participantId; this.specialistId = specialistId; this.purpose = purpose; this.scopes = scopes;
        decisionSource = "TEST_DEFAULT"; acceptedByParticipant = false; this.createdBy = createdBy; this.createdAt = createdAt;
    }
    UUID id() { return id; }
    boolean permits(String requestedPurpose, java.util.Set<String> requestedScopes) {
        if (!purpose.equals(requestedPurpose) || !"TEST_DEFAULT".equals(decisionSource)) return false;
        return java.util.Arrays.stream(scopes.split(",")).map(String::trim)
                .collect(java.util.stream.Collectors.toSet()).containsAll(requestedScopes);
    }
}
