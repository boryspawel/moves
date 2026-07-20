package com.motionecosystem.consent;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LegalAcknowledgementRepository extends JpaRepository<LegalAcknowledgement, UUID> {
    List<LegalAcknowledgement> findByAccountIdOrderByAcceptedAt(UUID accountId);
    boolean existsByAccountIdAndTypeAndDocumentVersion(UUID accountId, AcknowledgementType type, String version);
}
