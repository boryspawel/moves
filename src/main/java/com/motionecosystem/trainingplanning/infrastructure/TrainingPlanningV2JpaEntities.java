package com.motionecosystem.trainingplanning.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.motionecosystem.trainingplanning.TrainingPlanningModel;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Persistence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity(name = "PlanRevisionJpaEntity")
@Table(name = "plan_revision", schema = "training_planning")
class PlanRevisionJpaEntity {
    @Id UUID id;
    @Column(name = "plan_id", nullable = false) UUID planId;
    @Column(name = "revision_number", nullable = false) int revisionNumber;
    @Column(name = "based_on_revision_id") UUID basedOnRevisionId;
    @Column(nullable = false) String status;
    @Column(name = "phase_intent", nullable = false, length = 500) String phaseIntent;
    @Column(name = "valid_from") LocalDate validFrom;
    @Column(name = "valid_to") LocalDate validTo;
    @Column(name = "author_account_id", nullable = false) UUID authorAccountId;
    @Column(name = "author_capability", nullable = false) String authorCapability;
    @Column(name = "migration_origin", nullable = false) String migrationOrigin;
    @Column(name = "assessment_status", nullable = false) String assessmentStatus;
    @Column(name = "draft_updated_at", nullable = false) Instant draftUpdatedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "validation_checksum") String validationChecksum;
    @Column(name = "load_snapshot_id") UUID loadSnapshotId;
    @Column(name = "safety_assessment_id") UUID safetyAssessmentId;
    @Column(name = "workflow_validated_at") Instant workflowValidatedAt;
    @Version long version;

    protected PlanRevisionJpaEntity() {
    }

    PlanRevisionJpaEntity(TrainingPlanningModel.Revision source) {
        id = source.id();
        planId = source.planId();
        revisionNumber = source.number();
        basedOnRevisionId = source.basedOnRevisionId();
        status = source.status().name();
        phaseIntent = source.phaseIntent();
        validFrom = source.validFrom();
        validTo = source.validTo();
        authorAccountId = source.authorAccountId();
        authorCapability = source.authorCapability();
        migrationOrigin = source.migrationOrigin();
        assessmentStatus = source.assessmentStatus();
        draftUpdatedAt = source.draftUpdatedAt();
        createdAt = source.createdAt();
    }

    void requireDraft(long expectedVersion, Instant now) {
        if (!"DRAFT".equals(status)) {
            throw new TrainingPlanningV2Persistence.ImmutableRevisionException();
        }
        if (version != expectedVersion) {
            throw new TrainingPlanningV2Persistence.RevisionConflictException();
        }
        draftUpdatedAt = now;
    }
}

@Entity(name = "GoalOutcomeJpaEntity")
@Table(name = "goal_outcome", schema = "training_planning")
class GoalOutcomeJpaEntity {
    @Id UUID id;
    @Column(name = "goal_id", nullable = false) UUID goalId;
    @Column(name = "metric_code", nullable = false) String metricCode;
    @Column(precision = 14, scale = 4) BigDecimal baseline;
    @Column(nullable = false, precision = 14, scale = 4) BigDecimal target;
    @Column(nullable = false) String unit;
    @Column(name = "measurement_method", nullable = false, length = 500) String measurementMethod;
    @Column(name = "evidence_source", length = 500) String evidenceSource;

    protected GoalOutcomeJpaEntity() {
    }

    GoalOutcomeJpaEntity(TrainingPlanningModel.GoalOutcome source) {
        id = source.id();
        goalId = source.goalId();
        metricCode = source.metricCode();
        baseline = source.baseline();
        target = source.target();
        unit = source.unit();
        measurementMethod = source.measurementMethod();
        evidenceSource = source.evidenceSource();
    }
}

@Entity(name = "PlanLoadBudgetJpaEntity")
@Table(name = "plan_load_budget", schema = "training_planning")
class PlanLoadBudgetJpaEntity {
    @Id UUID id;
    @Column(name = "revision_id", nullable = false) UUID revisionId;
    @Column(nullable = false) String channel;
    @Column(name = "budget_low", nullable = false, precision = 14, scale = 4) BigDecimal low;
    @Column(name = "budget_high", nullable = false, precision = 14, scale = 4) BigDecimal high;
    @Column(nullable = false) String unit;
    @Column(nullable = false) String action;
    @Column(name = "created_by_account_id", nullable = false) UUID createdByAccountId;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected PlanLoadBudgetJpaEntity() {
    }

    PlanLoadBudgetJpaEntity(TrainingPlanningModel.LoadBudget source) {
        id = source.id();
        revisionId = source.revisionId();
        channel = source.channel();
        low = source.low();
        high = source.high();
        unit = source.unit();
        action = source.action().name();
        createdByAccountId = source.createdByAccountId();
        createdAt = source.createdAt();
    }
}

@Entity(name = "PlanStructuralValidationJpaEntity")
@Table(name = "plan_revision_structural_validation", schema = "training_planning")
class PlanStructuralValidationJpaEntity {
    @Id UUID id;
    @Column(name = "revision_id", nullable = false) UUID revisionId;
    @Column(name = "draft_version", nullable = false) long draftVersion;
    @Column(name = "input_checksum", nullable = false, length = 64) String inputChecksum;
    @Column(nullable = false) String result;
    @Column(name = "validated_at", nullable = false) Instant validatedAt;
    @Column(name = "validated_by_account_id", nullable = false) UUID validatedByAccountId;

    protected PlanStructuralValidationJpaEntity() {
    }

    PlanStructuralValidationJpaEntity(TrainingPlanningModel.StructuralValidation source) {
        id = source.id();
        revisionId = source.revisionId();
        draftVersion = source.draftVersion();
        inputChecksum = source.inputChecksum();
        result = source.result().name();
        validatedAt = source.validatedAt();
        validatedByAccountId = source.validatedByAccountId();
    }
}

@Entity(name = "PlanStructuralViolationJpaEntity")
@Table(name = "plan_revision_structural_violation", schema = "training_planning")
class PlanStructuralViolationJpaEntity {
    @jakarta.persistence.EmbeddedId PlanStructuralViolationId id;
    @Column(nullable = false) String code;

    protected PlanStructuralViolationJpaEntity() {
    }

    PlanStructuralViolationJpaEntity(UUID validationId, int position, String code) {
        id = new PlanStructuralViolationId(validationId, position);
        this.code = code;
    }
}

@jakarta.persistence.Embeddable
record PlanStructuralViolationId(
        @Column(name = "validation_id") UUID validationId,
        @Column(name = "position") int position) implements java.io.Serializable {
}
