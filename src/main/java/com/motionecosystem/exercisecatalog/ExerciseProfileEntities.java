package com.motionecosystem.exercisecatalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "ExerciseContributionJpaEntity")
@Table(name = "exercise_contribution", schema = "exercise_catalog")
class ExerciseContribution {
    @Id UUID id;
    @Column(name = "exercise_version_id", nullable = false) UUID exerciseVersionId;
    @Column(name = "anatomical_structure_id", nullable = false) UUID anatomicalStructureId;
    @Enumerated(EnumType.STRING) @Column(name = "contribution_role", nullable = false) ContributionRole role;
    @Enumerated(EnumType.STRING) @Column(name = "load_channel", nullable = false) LoadChannel loadChannel;
    @Enumerated(EnumType.STRING) @Column(name = "contribution_band", nullable = false) ContributionBand contributionBand;
    @Column(name = "coefficient_low", nullable = false, precision = 9, scale = 6) BigDecimal coefficientLow;
    @Column(name = "coefficient_high", nullable = false, precision = 9, scale = 6) BigDecimal coefficientHigh;
    @Column(name = "confidence_class", nullable = false, length = 80) String confidenceClass;
    @Column(name = "evidence_grade", nullable = false, length = 80) String evidenceGrade;
    @Enumerated(EnumType.STRING) @Column(name = "calculation_role", nullable = false) CalculationRole calculationRole;
    @Column(name = "variant_condition", length = 120) String variantCondition;
    @Enumerated(EnumType.STRING) @Column(name = "side_rule", nullable = false) ContributionSideRule sideRule;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "created_by_subject", nullable = false, updatable = false) String createdBySubject;

    protected ExerciseContribution() {
    }

    ExerciseContribution(UUID exerciseVersionId, UUID anatomicalStructureId, ContributionRole role,
                         LoadChannel loadChannel, ContributionBand contributionBand,
                         BigDecimal coefficientLow, BigDecimal coefficientHigh,
                         String confidenceClass, String evidenceGrade, CalculationRole calculationRole,
                         String variantCondition, ContributionSideRule sideRule,
                         String createdBySubject, Instant createdAt) {
        id = UUID.randomUUID();
        this.exerciseVersionId = exerciseVersionId;
        this.anatomicalStructureId = anatomicalStructureId;
        this.role = role;
        this.loadChannel = loadChannel;
        this.contributionBand = contributionBand;
        this.coefficientLow = coefficientLow;
        this.coefficientHigh = coefficientHigh;
        this.confidenceClass = confidenceClass;
        this.evidenceGrade = evidenceGrade;
        this.calculationRole = calculationRole;
        this.variantCondition = variantCondition;
        this.sideRule = sideRule;
        this.createdBySubject = createdBySubject;
        this.createdAt = createdAt;
    }
}

@Entity(name = "ExerciseLoadCharacteristicJpaEntity")
@Table(name = "exercise_load_characteristic", schema = "exercise_catalog")
class ExerciseLoadCharacteristic {
    @Id UUID id;
    @Column(name = "exercise_version_id", nullable = false) UUID exerciseVersionId;
    @Enumerated(EnumType.STRING) @Column(name = "movement_plane", nullable = false) MovementPlane movementPlane;
    @Enumerated(EnumType.STRING) @Column(name = "contraction_type", nullable = false) ContractionType contractionType;
    @Enumerated(EnumType.STRING) @Column(name = "range_of_motion", nullable = false) RangeOfMotion rangeOfMotion;
    @Enumerated(EnumType.STRING) @Column(name = "characteristic_type", nullable = false) LoadCharacteristicType characteristicType;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "created_by_subject", nullable = false, updatable = false) String createdBySubject;

    protected ExerciseLoadCharacteristic() {
    }

    ExerciseLoadCharacteristic(UUID exerciseVersionId, MovementPlane movementPlane,
                               ContractionType contractionType, RangeOfMotion rangeOfMotion,
                               LoadCharacteristicType characteristicType,
                               String createdBySubject, Instant createdAt) {
        id = UUID.randomUUID();
        this.exerciseVersionId = exerciseVersionId;
        this.movementPlane = movementPlane;
        this.contractionType = contractionType;
        this.rangeOfMotion = rangeOfMotion;
        this.characteristicType = characteristicType;
        this.createdBySubject = createdBySubject;
        this.createdAt = createdAt;
    }
}

@Entity(name = "EvidenceSourceJpaEntity")
@Table(name = "evidence_source", schema = "exercise_catalog")
class EvidenceSource {
    @Id UUID id;
    @Column(name = "exercise_version_id", nullable = false) UUID exerciseVersionId;
    @Column(nullable = false, length = 500) String citation;
    @Column(name = "source_uri", length = 1000) String sourceUri;
    @Column(name = "evidence_grade", nullable = false, length = 80) String evidenceGrade;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "created_by_subject", nullable = false, updatable = false) String createdBySubject;

    protected EvidenceSource() {
    }

    EvidenceSource(UUID exerciseVersionId, String citation, String sourceUri, String evidenceGrade,
                   String createdBySubject, Instant createdAt) {
        id = UUID.randomUUID();
        this.exerciseVersionId = exerciseVersionId;
        this.citation = citation;
        this.sourceUri = sourceUri;
        this.evidenceGrade = evidenceGrade;
        this.createdBySubject = createdBySubject;
        this.createdAt = createdAt;
    }
}

@Entity(name = "ExerciseContributionEvidenceJpaEntity")
@Table(name = "exercise_contribution_evidence", schema = "exercise_catalog")
class ExerciseContributionEvidence {
    @Id UUID id;
    @Column(name = "contribution_id", nullable = false) UUID contributionId;
    @Column(name = "evidence_source_id", nullable = false) UUID evidenceSourceId;

    protected ExerciseContributionEvidence() {
    }

    ExerciseContributionEvidence(UUID contributionId, UUID evidenceSourceId) {
        id = UUID.randomUUID();
        this.contributionId = contributionId;
        this.evidenceSourceId = evidenceSourceId;
    }
}
