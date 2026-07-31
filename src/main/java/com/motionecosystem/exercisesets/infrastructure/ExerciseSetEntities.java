package com.motionecosystem.exercisesets.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

final class ExerciseSetEntities { private ExerciseSetEntities() { }

@Entity @Table(name="exercise_set", schema="exercise_set")
static class ExerciseSetEntity {
    @Id UUID id; @Column(name="owner_account_id", nullable=false) UUID ownerAccountId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Visibility visibility = Visibility.PRIVATE;
    @Column(name="created_at", nullable=false, updatable=false) Instant createdAt; @Column(name="updated_at", nullable=false) Instant updatedAt;
    protected ExerciseSetEntity() { }
}

@Entity @Table(name="exercise_set_version", schema="exercise_set", uniqueConstraints=@UniqueConstraint(name="uq_exercise_set_version_number", columnNames={"exercise_set_id","version_number"}))
static class ExerciseSetVersionEntity {
    @Id UUID id; @Column(name="exercise_set_id", nullable=false) UUID exerciseSetId; @Column(name="version_number",nullable=false) int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false) VersionStatus status;
    @Enumerated(EnumType.STRING) SetProfile profile; @Column String title; @Column(columnDefinition="text") String description; @Column(name="target_level") String targetLevel;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false, columnDefinition="jsonb") String tags = "[]";
    @Enumerated(EnumType.STRING) @Column(name="variant_kind",nullable=false) VariantKind variantKind = VariantKind.BASE; @Column(name="variant_of_version_id") UUID variantOfVersionId;
    @Column(name="author_account_id",nullable=false) UUID authorAccountId; @Column(name="created_at",nullable=false,updatable=false) Instant createdAt; @Column(name="updated_at",nullable=false) Instant updatedAt; @Column(name="published_at") Instant publishedAt; @Column(name="retired_at") Instant retiredAt; @Version long version;
    @OneToMany(mappedBy="version", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("position ASC") List<ExerciseSetItemEntity> items = new ArrayList<>();
    @OneToOne(mappedBy="version", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.LAZY) ExerciseSetAnalysisRunEntity analysisRun;
    @OneToOne(mappedBy="version", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.LAZY) ExerciseSetAnatomyAnalysisRunEntity anatomyAnalysisRun;
    protected ExerciseSetVersionEntity() { }
}

@Entity @Table(name="exercise_set_analysis_run", schema="exercise_set")
static class ExerciseSetAnalysisRunEntity {
    @Id UUID id; @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="exercise_set_version_id", nullable=false, unique=true) ExerciseSetVersionEntity version;
    @Column(name="policy_version", nullable=false) String policyVersion; @Column(name="analyzed_lock_version", nullable=false) long analyzedLockVersion;
    @Column(name="analyzed_at", nullable=false) Instant analyzedAt; @Column(nullable=false) String status;
    @Column(name="item_count", nullable=false) int itemCount; @Column(name="estimated_seconds") Integer estimatedSeconds; @Enumerated(EnumType.STRING) @Column(name="time_confidence", nullable=false) ExerciseSetDtos.TimeConfidence timeConfidence;
    @Column(name="equipment_transitions", nullable=false) int equipmentTransitions; @Column(name="dose_kind_switches", nullable=false) int doseKindSwitches;
    @OneToMany(mappedBy="run", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("findingOrdinal ASC") List<ExerciseSetAnalysisFindingEntity> findings = new ArrayList<>();
}

@Entity @Table(name="exercise_set_analysis_finding", schema="exercise_set")
static class ExerciseSetAnalysisFindingEntity {
    @Id UUID id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="analysis_run_id", nullable=false) ExerciseSetAnalysisRunEntity run; @Column(name="finding_ordinal", nullable=false) int findingOrdinal;
    @Column(nullable=false) String code; @Column(name="rule_version", nullable=false) String ruleVersion; @Enumerated(EnumType.STRING) @Column(nullable=false) ExerciseSetDtos.FindingSeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable=false) ExerciseSetDtos.FindingCategory category; @Column(name="message_key", nullable=false) String messageKey;
    @Column(columnDefinition="text") String explanation; @JdbcTypeCode(SqlTypes.JSON) @Column(name="item_ids", nullable=false, columnDefinition="jsonb") String itemIds="[]";
    @Enumerated(EnumType.STRING) Phase phase; String field; String action; @Column(nullable=false) boolean blocking;
}

@Entity @Table(name="exercise_set_anatomy_analysis_run", schema="exercise_set")
static class ExerciseSetAnatomyAnalysisRunEntity {
    @Id UUID id; @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="exercise_set_version_id", nullable=false, unique=true) ExerciseSetVersionEntity version;
    @Column(name="policy_version", nullable=false) String policyVersion; @Column(name="analyzed_lock_version", nullable=false) long analyzedLockVersion;
    @Column(name="analyzed_at", nullable=false) Instant analyzedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false, columnDefinition="jsonb") String result = "{}";
}

@Entity @Table(name="exercise_set_item", schema="exercise_set", uniqueConstraints=@UniqueConstraint(name="uq_exercise_set_item_position",columnNames={"exercise_set_version_id","position"}))
static class ExerciseSetItemEntity {
    @Id UUID id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="exercise_set_version_id",nullable=false) ExerciseSetVersionEntity version;
    @Column(name="exercise_version_id",nullable=false) UUID exerciseVersionId; @Enumerated(EnumType.STRING) @Column(nullable=false) Phase phase; @Column(nullable=false) int position;
    @Column(name="canonical_name",nullable=false) String canonicalName; @Column(name="exercise_version_number",nullable=false) int exerciseVersionNumber; @Column(name="profile_schema_version",nullable=false) int profileSchemaVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="movement_patterns",nullable=false,columnDefinition="jsonb") String movementPatterns="[]"; @JdbcTypeCode(SqlTypes.JSON) @Column(name="required_equipment",nullable=false,columnDefinition="jsonb") String requiredEquipment="[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="anatomy_snapshot",nullable=false,columnDefinition="jsonb") String anatomySnapshot="{}";
    @Column(name="participant_instruction",columnDefinition="text") String participantInstruction; @Column(name="specialist_instruction",columnDefinition="text") String specialistInstruction;
    @OneToOne(mappedBy="item",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.LAZY) DoseEntity dose;
    protected ExerciseSetItemEntity() { }
}

@Entity @Table(name="exercise_set_item_dose",schema="exercise_set") @Inheritance(strategy=InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name="kind",discriminatorType=DiscriminatorType.STRING) abstract static class DoseEntity {
    @Id UUID id; @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="item_id",nullable=false,unique=true) ExerciseSetItemEntity item;
    Integer sets; Integer reps; @Column(name="rep_min") Integer repMin; @Column(name="rep_max") Integer repMax; @Column(name="hold_seconds") Integer holdSeconds; @Column(name="duration_seconds") Integer durationSeconds; Integer cycles; @Column(name="rest_seconds") Integer restSeconds; String tempo;
    @Column(name="load_value") BigDecimal loadValue; @Column(name="load_unit") String loadUnit; BigDecimal rpe; Integer rir; String intensity; @Enumerated(EnumType.STRING) Side side; @Column(name="range_target") String rangeTarget; Integer repetitions; String rhythm; @Column(name="distance_meters") Integer distanceMeters; String zone;
}
@Entity @DiscriminatorValue("STRENGTH") static class StrengthDoseEntity extends DoseEntity { }
@Entity @DiscriminatorValue("ISOMETRIC") static class IsometricDoseEntity extends DoseEntity { }
@Entity @DiscriminatorValue("MOBILITY") static class MobilityDoseEntity extends DoseEntity { }
@Entity @DiscriminatorValue("STRETCH") static class StretchDoseEntity extends DoseEntity { }
@Entity @DiscriminatorValue("BREATHING") static class BreathingDoseEntity extends DoseEntity { }
@Entity @DiscriminatorValue("AEROBIC") static class AerobicDoseEntity extends DoseEntity { }
}
