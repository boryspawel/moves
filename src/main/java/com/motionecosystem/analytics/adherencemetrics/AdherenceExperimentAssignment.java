package com.motionecosystem.analytics.adherencemetrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adherence_experiment_assignment", schema = "analytics")
class AdherenceExperimentAssignment {
    @Id UUID id;
    @Column(name = "participant_account_id", nullable = false) UUID participantAccountId;
    @Column(name = "experiment_key", nullable = false) String experimentKey;
    @Column(name = "experiment_version", nullable = false) int experimentVersion;
    @Column(name = "variant_code", nullable = false) String variantCode;
    @Column(name = "assigned_at", nullable = false) Instant assignedAt;

    protected AdherenceExperimentAssignment() { }
    AdherenceExperimentAssignment(UUID participantAccountId, String experimentKey, int experimentVersion,
                                  String variantCode, Instant assignedAt) {
        id = UUID.randomUUID(); this.participantAccountId = participantAccountId; this.experimentKey = experimentKey;
        this.experimentVersion = experimentVersion; this.variantCode = variantCode; this.assignedAt = assignedAt;
    }
}
