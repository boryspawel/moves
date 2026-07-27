package com.motionecosystem.specialist;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface TestDefaultConsentOverrideRepository extends JpaRepository<TestDefaultConsentOverride, UUID> {
    boolean existsByParticipantIdAndSpecialistId(UUID participantId, UUID specialistId);
    java.util.Optional<TestDefaultConsentOverride> findByParticipantIdAndSpecialistIdAndPurpose(UUID participantId, UUID specialistId, String purpose);
}
