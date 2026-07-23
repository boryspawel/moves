package com.motionecosystem.participant;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantProfileRepository extends JpaRepository<ParticipantProfile, UUID> {
    Optional<ParticipantProfile> findByAccountId(UUID accountId);
    List<ParticipantProfile> findByAccountIdIn(Collection<UUID> accountIds);
}
