package com.motionecosystem.safety.api;

import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Neutral, immutable boundary used by plan activation orchestration. */
public interface SafetyAssessmentPort {

    AssessmentSnapshot assess(
            UUID participantAccountId,
            PlanRevisionSnapshot revision,
            LoadProfile loadProfile);

    Optional<AssessmentSnapshot> findAssessment(UUID assessmentId, Instant effectiveAt);

    enum Result {
        PASS,
        INFO,
        WARNING,
        HARD_BLOCK
    }

    record FactorSnapshot(
            UUID id,
            Result result,
            String ruleCode,
            String targetRef,
            String channel,
            BigDecimal observedLow,
            BigDecimal observedHigh,
            BigDecimal thresholdLow,
            BigDecimal thresholdHigh,
            String unit,
            String explanationCode,
            String evidenceGrade,
            boolean overridable,
            boolean activelyOverridden) {
    }

    record AssessmentSnapshot(
            UUID id,
            UUID participantAccountId,
            UUID revisionId,
            UUID loadSnapshotId,
            String loadCalculationVersion,
            String rulesetCode,
            int rulesetVersion,
            Result recordedResult,
            Result effectiveResult,
            Instant assessedAt,
            List<FactorSnapshot> factors) {

        public AssessmentSnapshot {
            factors = List.copyOf(factors);
        }
    }
}
