package com.motionecosystem.consent;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TestDefaultConsentOverrideRepository extends JpaRepository<TestDefaultConsentOverride, UUID> {
    Optional<TestDefaultConsentOverride> findByParticipantIdAndSpecialistIdAndPurpose(UUID participantId, UUID specialistId, String purpose);
}
