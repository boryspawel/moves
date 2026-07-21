package com.motionecosystem.loadanalysis.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;

public interface PlannedLoadCalculationPort {
    LoadProfile calculate(PlanRevisionSnapshot revisionSnapshot, LoadCalculationVersion version);

    record LoadCalculationVersion(String algorithmVersion, String configurationVersion) {
    }

    record LoadProfile(UUID snapshotId, UUID revisionId, String inputChecksum,
                       String algorithmVersion, String configurationVersion,
                       String catalogProfileVersion, Instant calculatedAt,
                       List<Observation> observations, List<Aggregate> aggregates) {
        public LoadProfile {
            observations = List.copyOf(observations);
            aggregates = List.copyOf(aggregates);
        }
    }

    record Observation(UUID prescriptionId, UUID exerciseVersionId, UUID contributionId,
                       UUID sessionId, UUID microcycleId, UUID cycleId, UUID structureId,
                       String side, String channel, String observationFamily, String unit,
                       BigDecimal low, BigDecimal high, String confidence,
                       String evidenceGrade, String doseSource, String observationMode) {
    }

    record Aggregate(String scope, String scopeKey, UUID structureId, String side,
                     String channel, String observationFamily, String unit,
                     BigDecimal low, BigDecimal high) {
    }
}
