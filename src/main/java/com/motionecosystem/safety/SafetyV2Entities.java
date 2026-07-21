package com.motionecosystem.safety;

import com.motionecosystem.safety.api.SafetyAssessmentPort.Result;
import com.motionecosystem.safety.domain.SafetyRules.SemanticType;
import com.motionecosystem.safety.domain.SafetyRules.SourceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "restriction", schema = "safety")
class RestrictionEntity {

    enum Status {
        ACTIVE,
        SUPERSEDED,
        WITHDRAWN
    }

    @Id
    UUID id;
    @Column(name = "root_id")
    UUID rootId;
    @Column(name = "revision_number")
    int revisionNumber;
    @Column(name = "supersedes_restriction_id")
    UUID supersedesRestrictionId;
    @Column(name = "participant_account_id")
    UUID participantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    SourceType sourceType;
    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_type")
    SemanticType semanticType;
    @Enumerated(EnumType.STRING)
    Status status;
    @Column(name = "valid_from")
    Instant validFrom;
    @Column(name = "valid_to")
    Instant validTo;
    @Column(name = "author_account_id")
    UUID authorAccountId;
    @Column(name = "author_capability")
    String authorCapability;
    @Column(name = "participant_explanation")
    String participantExplanation;
    @Column(name = "clinical_rationale_ref")
    String clinicalRationaleRef;
    @Column(name = "created_at")
    Instant createdAt;
    @Version
    long version;

    @OneToOne(mappedBy = "restriction", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    RestrictionTargetEntity target;

    protected RestrictionEntity() {
    }

    static RestrictionEntity initial(
            UUID participantId,
            SourceType sourceType,
            SemanticType semanticType,
            UUID author,
            String capability,
            String explanation,
            String clinicalReference,
            Instant validFrom,
            Instant validTo,
            RestrictionTargetEntity target,
            Instant now) {
        RestrictionEntity item = new RestrictionEntity();
        item.id = UUID.randomUUID();
        item.rootId = item.id;
        item.revisionNumber = 1;
        item.participantId = participantId;
        item.sourceType = sourceType;
        item.semanticType = semanticType;
        item.status = Status.ACTIVE;
        item.validFrom = validFrom;
        item.validTo = validTo;
        item.authorAccountId = author;
        item.authorCapability = capability;
        item.participantExplanation = explanation;
        item.clinicalRationaleRef = clinicalReference;
        item.createdAt = now;
        item.target = target;
        target.restriction = item;
        return item;
    }

    boolean activeAt(Instant instant) {
        return status == Status.ACTIVE
                && !instant.isBefore(validFrom)
                && (validTo == null || !instant.isAfter(validTo));
    }

    void withdraw() {
        status = Status.WITHDRAWN;
    }
}

@Entity
@Table(name = "restriction_target", schema = "safety")
class RestrictionTargetEntity {
    @Id
    @Column(name = "restriction_id")
    UUID restrictionId;
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "restriction_id")
    RestrictionEntity restriction;
    @Column(name = "structure_id")
    UUID structureId;
    @Column(name = "movement_pattern")
    String movementPattern;
    String channel;
    @Column(name = "load_characteristic")
    String loadCharacteristic;
    String side;
    @Column(name = "range_of_motion")
    String rangeOfMotion;
    @Column(name = "contraction_type")
    String contractionType;
    @Column(name = "limit_low")
    BigDecimal limitLow;
    @Column(name = "limit_high")
    BigDecimal limitHigh;
    String unit;
    @Column(name = "minimum_recovery_hours")
    Integer minimumRecoveryHours;

    protected RestrictionTargetEntity() {
    }
}

@Entity
@Table(name = "plan_safety_assessment", schema = "safety")
class SafetyAssessmentEntity {
    @Id
    UUID id;
    @Column(name = "participant_account_id")
    UUID participantId;
    @Column(name = "revision_id")
    UUID revisionId;
    @Column(name = "load_snapshot_id")
    UUID loadSnapshotId;
    @Column(name = "load_input_checksum")
    String loadInputChecksum;
    @Column(name = "load_calculation_version")
    String loadCalculationVersion;
    @Column(name = "ruleset_code")
    String rulesetCode;
    @Column(name = "ruleset_version")
    int rulesetVersion;
    @Enumerated(EnumType.STRING)
    Result result;
    @Column(name = "restriction_snapshot")
    String restrictionSnapshot;
    @Column(name = "ruleset_snapshot")
    String rulesetSnapshot;
    @Column(name = "load_snapshot")
    String loadSnapshot;
    @Column(name = "assessed_at")
    Instant assessedAt;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<SafetyFactorEntity> factors = new ArrayList<>();

    protected SafetyAssessmentEntity() {
    }
}

@Entity
@Table(name = "assessment_factor", schema = "safety")
class SafetyFactorEntity {
    @Id
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    SafetyAssessmentEntity assessment;
    @Enumerated(EnumType.STRING)
    Result result;
    @Column(name = "rule_code")
    String ruleCode;
    @Column(name = "target_ref")
    String targetRef;
    String channel;
    @Column(name = "observed_low")
    BigDecimal observedLow;
    @Column(name = "observed_high")
    BigDecimal observedHigh;
    @Column(name = "threshold_low")
    BigDecimal thresholdLow;
    @Column(name = "threshold_high")
    BigDecimal thresholdHigh;
    String unit;
    @Column(name = "explanation_code")
    String explanationCode;
    @Column(name = "evidence_grade")
    String evidenceGrade;
    boolean overridable;

    protected SafetyFactorEntity() {
    }
}

@Entity
@Table(name = "assessment_override", schema = "safety")
class SafetyOverrideEntity {
    @Id
    UUID id;
    @Column(name = "assessment_id")
    UUID assessmentId;
    @Column(name = "factor_id")
    UUID factorId;
    @Column(name = "actor_account_id")
    UUID actorId;
    @Column(name = "actor_capability")
    String actorCapability;
    @Column(name = "reason_code")
    String reasonCode;
    String scope;
    @Column(name = "valid_from")
    Instant validFrom;
    @Column(name = "valid_to")
    Instant validTo;
    @Column(name = "created_at")
    Instant createdAt;

    protected SafetyOverrideEntity() {
    }

    boolean activeAt(Instant instant) {
        return !instant.isBefore(validFrom) && !instant.isAfter(validTo);
    }
}
