CREATE SCHEMA exercise_catalog;
CREATE SCHEMA safety;

CREATE TABLE exercise_catalog.exercise (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL
);

CREATE TABLE exercise_catalog.exercise_version (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES exercise_catalog.exercise (id),
    version_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    instruction TEXT NOT NULL,
    media_reference TEXT,
    movement_pattern VARCHAR(64) NOT NULL,
    stimulus_type VARCHAR(64) NOT NULL,
    fatigue_profile VARCHAR(32) NOT NULL,
    technical_level VARCHAR(32) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_exercise_version UNIQUE (exercise_id, version_number),
    CONSTRAINT ck_exercise_version_number CHECK (version_number > 0),
    CONSTRAINT ck_exercise_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN')),
    CONSTRAINT ck_exercise_version_fatigue CHECK (fatigue_profile IN ('LOW', 'MODERATE', 'HIGH')),
    CONSTRAINT ck_exercise_version_technical CHECK (technical_level IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_exercise_version_environment CHECK (environment IN ('HOME', 'GYM', 'OUTDOOR', 'CLINIC', 'ANY'))
);
CREATE INDEX ix_exercise_version_status ON exercise_catalog.exercise_version (status);
CREATE INDEX ix_exercise_version_filters
    ON exercise_catalog.exercise_version (movement_pattern, technical_level, environment);

CREATE TABLE exercise_catalog.exercise_version_equipment (
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version (id) ON DELETE CASCADE,
    equipment VARCHAR(80) NOT NULL,
    PRIMARY KEY (exercise_version_id, equipment)
);

CREATE TABLE exercise_catalog.exercise_version_contraindication (
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version (id) ON DELETE CASCADE,
    contraindication_tag VARCHAR(80) NOT NULL,
    PRIMARY KEY (exercise_version_id, contraindication_tag)
);

CREATE TABLE safety.participant_restriction (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    contraindication_tag VARCHAR(80) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_participant_restriction UNIQUE (account_id, contraindication_tag)
);

CREATE TABLE safety.readiness_check_in (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    pain_level INTEGER NOT NULL,
    readiness_level INTEGER NOT NULL,
    pain_area VARCHAR(120),
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_readiness_pain CHECK (pain_level BETWEEN 0 AND 10),
    CONSTRAINT ck_readiness_level CHECK (readiness_level BETWEEN 1 AND 5)
);
CREATE INDEX ix_readiness_account_recorded
    ON safety.readiness_check_in (account_id, recorded_at DESC);
