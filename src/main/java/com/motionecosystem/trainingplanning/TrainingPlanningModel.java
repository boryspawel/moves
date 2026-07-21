package com.motionecosystem.trainingplanning;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TrainingPlanningModel {
    private TrainingPlanningModel() {
    }

    public enum PlanMode { SPECIALIST, SELF_DIRECTED, COLLABORATIVE }
    public enum PlanStatus { DRAFT, ACTIVE, COMPLETED, ARCHIVED }
    public enum RevisionStatus { DRAFT, VALIDATING, READY, ACTIVE, SUPERSEDED, COMPLETED }
    public enum GoalPerspective { PERFORMANCE, FUNCTIONAL_RECOVERY, GENERAL_FITNESS }
    public enum GoalStatus { ACTIVE, ACHIEVED, CANCELLED }
    public enum PrescriptionSide { LEFT, RIGHT, BILATERAL, NOT_APPLICABLE }
    public enum DoseType { DYNAMIC_RESISTANCE, ISOMETRIC, IMPACT, ENDURANCE, MOBILITY_CONTROL }
    public enum IntensityType { RPE, RIR, PERCENT_1RM, ZONE }
    public enum BudgetAction { INFO, WARNING }
    public enum ValidationResult { PASS, FAIL }

    public record PlanDraft(UUID id, UUID participantAccountId, String name, String purpose,
                            UUID ownerAccountId, PlanMode mode, PlanStatus status,
                            UUID currentRevisionId, UUID createdByAccountId, Instant createdAt) {
    }

    public record Revision(UUID id, UUID planId, int number, UUID basedOnRevisionId,
                           RevisionStatus status, String phaseIntent, LocalDate validFrom,
                           LocalDate validTo, UUID authorAccountId, String authorCapability,
                           String migrationOrigin, String assessmentStatus,
                           Instant draftUpdatedAt, Instant createdAt, long version) {
    }

    public record Goal(UUID id, UUID revisionId, UUID participantAccountId,
                       GoalPerspective perspective, String category, String title,
                       String description, int priority, GoalStatus status,
                       LocalDate targetDate, UUID createdByAccountId, Instant createdAt) {
    }

    public record GoalOutcome(UUID id, UUID goalId, String metricCode, BigDecimal baseline,
                              BigDecimal target, String unit, String measurementMethod,
                              String evidenceSource) {
    }

    public record Cycle(UUID id, UUID planId, UUID revisionId, int sequenceNumber,
                        String name, LocalDate startDate, LocalDate endDate,
                        String phaseIntent, String phaseGoal) {
    }

    public record MicrocycleV2(UUID id, UUID cycleId, int sequenceNumber, String name,
                               LocalDate startDate, LocalDate endDate,
                               String phaseIntent, String phaseGoal) {
    }

    public record Session(UUID id, UUID microcycleId, UUID participantAccountId, String title,
                          LocalDate scheduledDate, Instant availableFrom, Instant availableTo,
                          int expectedDurationMinutes, Instant createdAt) {
    }

    public record Prescription(UUID id, UUID plannedSessionId, UUID exerciseVersionId,
                               int position, PrescriptionSide side, DoseType doseType,
                               Integer sets, Integer repetitions, Integer durationSeconds,
                               BigDecimal distanceMeters, Integer contacts,
                               BigDecimal externalLoadValue, String externalLoadUnit,
                               IntensityType intensityType, BigDecimal intensityValue,
                               String intensityZone, String tempo, String rangeOfMotion,
                               Integer restSeconds, String substituteGroup, String notes) {
    }

    public record LoadBudget(UUID id, UUID revisionId, String channel,
                             BigDecimal low, BigDecimal high, String unit,
                             BudgetAction action, UUID createdByAccountId, Instant createdAt) {
    }

    public record StructuralValidation(UUID id, UUID revisionId, long draftVersion,
                                       String inputChecksum, ValidationResult result,
                                       List<String> violations, Instant validatedAt,
                                       UUID validatedByAccountId) {
    }
}
