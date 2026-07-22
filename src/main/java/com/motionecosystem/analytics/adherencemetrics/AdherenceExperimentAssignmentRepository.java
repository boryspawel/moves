package com.motionecosystem.analytics.adherencemetrics;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdherenceExperimentAssignmentRepository extends JpaRepository<AdherenceExperimentAssignment, UUID> {
    Optional<AdherenceExperimentAssignment> findByParticipantAccountIdAndExperimentKeyAndExperimentVersion(
            UUID participantAccountId, String experimentKey, int experimentVersion);
}
