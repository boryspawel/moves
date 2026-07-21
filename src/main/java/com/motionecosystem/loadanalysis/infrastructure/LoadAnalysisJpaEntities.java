package com.motionecosystem.loadanalysis.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.Aggregate;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.Observation;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Embeddable
record CalculationVersionId(@Column(name="algorithm_version") String algorithmVersion,
                            @Column(name="configuration_version") String configurationVersion)
        implements java.io.Serializable { }

@Entity @Table(name="load_calculation_version", schema="load_analysis")
class CalculationVersionEntity {
    @EmbeddedId CalculationVersionId id;
    @Column(name="created_at") Instant createdAt;
    protected CalculationVersionEntity() { }
    CalculationVersionEntity(String algorithm, String configuration, Instant now) {
        id = new CalculationVersionId(algorithm, configuration); createdAt = now;
    }
}

@Entity @Table(name="planned_load_snapshot", schema="load_analysis")
class LoadSnapshotEntity {
    @Id UUID id;
    @Column(name="revision_id") UUID revisionId;
    @Column(name="input_checksum") String checksum;
    @Column(name="algorithm_version") String algorithm;
    @Column(name="configuration_version") String configuration;
    @Column(name="catalog_profile_version") String catalogVersion;
    @Column(name="calculated_at") Instant calculatedAt;
    protected LoadSnapshotEntity() { }
    LoadSnapshotEntity(LoadProfile value) {
        id=value.snapshotId(); revisionId=value.revisionId(); checksum=value.inputChecksum();
        algorithm=value.algorithmVersion(); configuration=value.configurationVersion();
        catalogVersion=value.catalogProfileVersion(); calculatedAt=value.calculatedAt();
    }
}

@Entity @Table(name="planned_load_observation", schema="load_analysis")
class LoadObservationEntity {
    @Id UUID id;
    @Column(name="snapshot_id") UUID snapshotId;
    @Column(name="prescription_id") UUID prescriptionId;
    @Column(name="exercise_version_id") UUID exerciseVersionId;
    @Column(name="contribution_id") UUID contributionId;
    @Column(name="session_id") UUID sessionId;
    @Column(name="microcycle_id") UUID microcycleId;
    @Column(name="cycle_id") UUID cycleId;
    @Column(name="structure_id") UUID structureId;
    String side; String channel;
    @Column(name="observation_family") String family;
    String unit;
    @Column(name="value_low") BigDecimal low;
    @Column(name="value_high") BigDecimal high;
    String confidence;
    @Column(name="evidence_grade") String evidenceGrade;
    @Column(name="dose_source") String doseSource;
    @Column(name="observation_mode") String observationMode;
    protected LoadObservationEntity() { }
    LoadObservationEntity(UUID snapshotId, Observation value) {
        id=UUID.randomUUID(); this.snapshotId=snapshotId; prescriptionId=value.prescriptionId();
        exerciseVersionId=value.exerciseVersionId(); contributionId=value.contributionId();
        sessionId=value.sessionId(); microcycleId=value.microcycleId(); cycleId=value.cycleId();
        structureId=value.structureId(); side=value.side(); channel=value.channel(); family=value.observationFamily();
        unit=value.unit(); low=value.low(); high=value.high(); confidence=value.confidence();
        evidenceGrade=value.evidenceGrade(); doseSource=value.doseSource(); observationMode=value.observationMode();
    }
    Observation view() { return new Observation(prescriptionId,exerciseVersionId,contributionId,sessionId,
            microcycleId,cycleId,structureId,side,channel,family,unit,low,high,confidence,evidenceGrade,
            doseSource,observationMode); }
}

@Entity @Table(name="load_aggregate_projection", schema="load_analysis")
class LoadAggregateEntity {
    @Id UUID id;
    @Column(name="snapshot_id") UUID snapshotId;
    String scope;
    @Column(name="scope_key") String scopeKey;
    @Column(name="structure_id") UUID structureId;
    String side; String channel;
    @Column(name="observation_family") String family;
    String unit;
    @Column(name="value_low") BigDecimal low;
    @Column(name="value_high") BigDecimal high;
    protected LoadAggregateEntity() { }
    LoadAggregateEntity(UUID snapshotId, Aggregate value) {
        id=UUID.randomUUID(); this.snapshotId=snapshotId; scope=value.scope(); scopeKey=value.scopeKey();
        structureId=value.structureId(); side=value.side(); channel=value.channel();
        family=value.observationFamily(); unit=value.unit(); low=value.low(); high=value.high();
    }
    Aggregate view() { return new Aggregate(scope,scopeKey,structureId,side,channel,family,unit,low,high); }
}
