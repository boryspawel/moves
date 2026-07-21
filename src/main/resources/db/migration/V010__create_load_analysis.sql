CREATE SCHEMA IF NOT EXISTS load_analysis;

CREATE TABLE load_analysis.load_calculation_version (
    algorithm_version VARCHAR(80) NOT NULL,
    configuration_version VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (algorithm_version, configuration_version)
);

CREATE TABLE load_analysis.planned_load_snapshot (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES training_planning.plan_revision (id),
    input_checksum VARCHAR(64) NOT NULL,
    algorithm_version VARCHAR(80) NOT NULL,
    configuration_version VARCHAR(80) NOT NULL,
    catalog_profile_version VARCHAR(80) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_planned_load_snapshot_input UNIQUE
        (revision_id, input_checksum, algorithm_version, configuration_version),
    CONSTRAINT fk_planned_load_calculation_version FOREIGN KEY
        (algorithm_version, configuration_version)
        REFERENCES load_analysis.load_calculation_version (algorithm_version, configuration_version)
);

CREATE TABLE load_analysis.planned_load_observation (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES load_analysis.planned_load_snapshot (id) ON DELETE CASCADE,
    prescription_id UUID NOT NULL,
    exercise_version_id UUID NOT NULL,
    contribution_id UUID NOT NULL,
    session_id UUID NOT NULL,
    microcycle_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    side VARCHAR(24) NOT NULL,
    channel VARCHAR(40) NOT NULL,
    observation_family VARCHAR(40) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    value_low NUMERIC(20, 6) NOT NULL,
    value_high NUMERIC(20, 6) NOT NULL,
    confidence VARCHAR(40) NOT NULL,
    evidence_grade VARCHAR(40) NOT NULL,
    dose_source VARCHAR(80) NOT NULL,
    observation_mode VARCHAR(40) NOT NULL,
    CONSTRAINT ck_load_observation_range CHECK (value_low >= 0 AND value_low <= value_high)
);
CREATE INDEX ix_load_observation_snapshot ON load_analysis.planned_load_observation (snapshot_id);

CREATE TABLE load_analysis.load_aggregate_projection (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES load_analysis.planned_load_snapshot (id) ON DELETE CASCADE,
    scope VARCHAR(32) NOT NULL,
    scope_key VARCHAR(80) NOT NULL,
    structure_id UUID NOT NULL,
    side VARCHAR(24) NOT NULL,
    channel VARCHAR(40) NOT NULL,
    observation_family VARCHAR(40) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    value_low NUMERIC(20, 6) NOT NULL,
    value_high NUMERIC(20, 6) NOT NULL,
    CONSTRAINT uq_load_aggregate_dimension UNIQUE
        (snapshot_id, scope, scope_key, structure_id, side, channel, observation_family, unit),
    CONSTRAINT ck_load_aggregate_range CHECK (value_low >= 0 AND value_low <= value_high)
);
