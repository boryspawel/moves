package com.motionecosystem.specialist;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpecialistProfileRepository extends JpaRepository<SpecialistProfile, UUID> {
    Optional<SpecialistProfile> findByAccountId(UUID accountId);
}
