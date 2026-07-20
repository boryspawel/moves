package com.motionecosystem.safety;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantRestrictionRepository extends JpaRepository<ParticipantRestriction, UUID> {
    List<ParticipantRestriction> findByAccountIdOrderByContraindicationTag(UUID accountId);
    void deleteByAccountId(UUID accountId);
}
