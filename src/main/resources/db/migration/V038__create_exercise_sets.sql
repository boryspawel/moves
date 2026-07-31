CREATE SCHEMA IF NOT EXISTS exercise_set;

CREATE TABLE exercise_set.exercise_set (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL REFERENCES identity_access.principal_account(id),
    visibility VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_exercise_set_visibility CHECK (visibility IN ('PRIVATE', 'SHARED', 'ORGANIZATION'))
);
CREATE INDEX ix_exercise_set_owner_created ON exercise_set.exercise_set(owner_account_id, created_at DESC);

CREATE TABLE exercise_set.exercise_set_version (
    id UUID PRIMARY KEY,
    exercise_set_id UUID NOT NULL REFERENCES exercise_set.exercise_set(id),
    version_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    profile VARCHAR(40),
    title VARCHAR(160),
    description TEXT,
    target_level VARCHAR(32),
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    variant_kind VARCHAR(16) NOT NULL DEFAULT 'BASE',
    variant_of_version_id UUID REFERENCES exercise_set.exercise_set_version(id),
    author_account_id UUID NOT NULL REFERENCES identity_access.principal_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_exercise_set_version_number UNIQUE(exercise_set_id, version_number),
    CONSTRAINT ck_exercise_set_version_number CHECK (version_number > 0),
    CONSTRAINT ck_exercise_set_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_exercise_set_variant_kind CHECK (variant_kind IN ('BASE', 'SHORT', 'MINIMUM')),
    CONSTRAINT ck_exercise_set_variant_source CHECK ((variant_kind = 'BASE' AND variant_of_version_id IS NULL) OR (variant_kind IN ('SHORT', 'MINIMUM') AND variant_of_version_id IS NOT NULL))
    ,CONSTRAINT ck_exercise_set_published_metadata CHECK (status = 'DRAFT' OR (title IS NOT NULL AND profile IS NOT NULL))
);
CREATE INDEX ix_exercise_set_version_set_status ON exercise_set.exercise_set_version(exercise_set_id, status, version_number DESC);
CREATE INDEX ix_exercise_set_version_variant_source ON exercise_set.exercise_set_version(variant_of_version_id);

CREATE TABLE exercise_set.exercise_set_item (
    id UUID PRIMARY KEY,
    exercise_set_version_id UUID NOT NULL REFERENCES exercise_set.exercise_set_version(id) ON DELETE CASCADE,
    exercise_version_id UUID NOT NULL,
    phase VARCHAR(16) NOT NULL,
    position INTEGER NOT NULL,
    canonical_name VARCHAR(160) NOT NULL,
    exercise_version_number INTEGER NOT NULL,
    profile_schema_version INTEGER NOT NULL,
    movement_patterns JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_equipment JSONB NOT NULL DEFAULT '[]'::jsonb,
    participant_instruction TEXT,
    specialist_instruction TEXT,
    CONSTRAINT uq_exercise_set_item_position UNIQUE(exercise_set_version_id, position),
    CONSTRAINT ck_exercise_set_item_position CHECK (position > 0),
    CONSTRAINT ck_exercise_set_item_phase CHECK (phase IN ('PREPARATION', 'MAIN', 'ACCESSORY', 'COOLDOWN')),
    CONSTRAINT ck_exercise_set_item_version_number CHECK (exercise_version_number > 0),
    CONSTRAINT ck_exercise_set_item_profile_schema CHECK (profile_schema_version > 0)
);
CREATE INDEX ix_exercise_set_item_exercise_version ON exercise_set.exercise_set_item(exercise_version_id);

CREATE TABLE exercise_set.exercise_set_item_dose (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL UNIQUE REFERENCES exercise_set.exercise_set_item(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    sets INTEGER,
    reps INTEGER,
    rep_min INTEGER,
    rep_max INTEGER,
    hold_seconds INTEGER,
    duration_seconds INTEGER,
    cycles INTEGER,
    rest_seconds INTEGER,
    tempo VARCHAR(32),
    load_value NUMERIC(10,2),
    load_unit VARCHAR(24),
    rpe NUMERIC(3,1),
    rir INTEGER,
    intensity VARCHAR(32),
    side VARCHAR(20),
    range_target VARCHAR(80),
    repetitions INTEGER,
    rhythm VARCHAR(48),
    distance_meters INTEGER,
    zone VARCHAR(24),
    CONSTRAINT ck_exercise_set_dose_kind CHECK (kind IN ('STRENGTH','ISOMETRIC','MOBILITY','STRETCH','BREATHING','AEROBIC')),
    CONSTRAINT ck_exercise_set_dose_positive CHECK ((sets IS NULL OR sets > 0) AND (reps IS NULL OR reps > 0) AND (rep_min IS NULL OR rep_min > 0) AND (rep_max IS NULL OR rep_max > 0) AND (hold_seconds IS NULL OR hold_seconds > 0) AND (duration_seconds IS NULL OR duration_seconds > 0) AND (cycles IS NULL OR cycles > 0) AND (rest_seconds IS NULL OR rest_seconds >= 0) AND (repetitions IS NULL OR repetitions > 0) AND (distance_meters IS NULL OR distance_meters > 0)),
    CONSTRAINT ck_exercise_set_dose_rep_range CHECK (rep_min IS NULL OR rep_max IS NULL OR rep_min <= rep_max),
    CONSTRAINT ck_exercise_set_dose_side CHECK (side IS NULL OR side IN ('NOT_APPLICABLE','LEFT','RIGHT','BILATERAL'))
    ,CONSTRAINT ck_exercise_set_dose_shape CHECK (
      (kind = 'STRENGTH' AND sets IS NOT NULL AND (reps IS NOT NULL OR (rep_min IS NOT NULL AND rep_max IS NOT NULL))) OR
      (kind = 'ISOMETRIC' AND sets IS NOT NULL AND hold_seconds IS NOT NULL) OR
      (kind = 'MOBILITY' AND (reps IS NOT NULL OR duration_seconds IS NOT NULL) AND range_target IS NOT NULL) OR
      (kind = 'STRETCH' AND hold_seconds IS NOT NULL AND repetitions IS NOT NULL AND side IS NOT NULL) OR
      (kind = 'BREATHING' AND (duration_seconds IS NOT NULL OR cycles IS NOT NULL)) OR
      (kind = 'AEROBIC' AND duration_seconds IS NOT NULL)
    )
);
