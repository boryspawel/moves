package com.motionecosystem.consent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motionecosystem.consent.api.ConsentDecisionPort.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

interface ConsentTemplateRepository extends JpaRepository<ConsentTemplateVersion, UUID> {
    Optional<ConsentTemplateVersion> findByIdAndStatus(
            UUID id, ConsentTemplateVersion.Status status);
}

interface ConsentGrantRepository extends JpaRepository<ConsentGrant, UUID> {
    @Override
    @EntityGraph(attributePaths = "scopes")
    Optional<ConsentGrant> findById(UUID id);

    @EntityGraph(attributePaths = "scopes")
    List<ConsentGrant> findByParticipantIdAndRecipientTypeAndRecipientIdAndPurposeAndStatus(
            UUID participant,
            ConsentGrant.RecipientType type,
            UUID recipient,
            Purpose purpose,
            ConsentGrant.Status status);
}
