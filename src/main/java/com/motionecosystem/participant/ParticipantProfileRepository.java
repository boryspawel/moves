package com.motionecosystem.participant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantProfileRepository extends JpaRepository<ParticipantProfile, UUID> {
    Optional<ParticipantProfile> findByAccountId(UUID accountId);
}
