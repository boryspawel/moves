ALTER TABLE exercise_catalog.exercise_version
    DROP CONSTRAINT ck_exercise_version_status;
ALTER TABLE exercise_catalog.exercise_version
    ADD CONSTRAINT ck_exercise_version_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'WITHDRAWN')
    ),
    ADD COLUMN profile_schema_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN reviewed_by_subject VARCHAR(255),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_exercise_profile_schema_version CHECK (profile_schema_version IN (1, 2)),
    ADD CONSTRAINT ck_exercise_review_pair CHECK (
        (reviewed_by_subject IS NULL AND reviewed_at IS NULL)
        OR (reviewed_by_subject IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_exercise_v2_review_state CHECK (
        profile_schema_version = 1
        OR (status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED') AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'PUBLISHED', 'WITHDRAWN') AND reviewed_at IS NOT NULL)
    );

CREATE TABLE exercise_catalog.exercise_version_movement_pattern (
    exercise_version_id UUID NOT NULL
        REFERENCES exercise_catalog.exercise_version (id) ON DELETE CASCADE,
    movement_pattern VARCHAR(64) NOT NULL,
    PRIMARY KEY (exercise_version_id, movement_pattern)
);
INSERT INTO exercise_catalog.exercise_version_movement_pattern (exercise_version_id, movement_pattern)
SELECT id, movement_pattern FROM exercise_catalog.exercise_version;
CREATE INDEX ix_exercise_version_movement_pattern
    ON exercise_catalog.exercise_version_movement_pattern (movement_pattern, exercise_version_id);

CREATE TABLE exercise_catalog.exercise_load_characteristic (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL
        REFERENCES exercise_catalog.exercise_version (id) ON DELETE CASCADE,
    movement_plane VARCHAR(32) NOT NULL,
    contraction_type VARCHAR(32) NOT NULL,
    range_of_motion VARCHAR(32) NOT NULL,
    characteristic_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    CONSTRAINT uq_exercise_load_characteristic UNIQUE (
        exercise_version_id, movement_plane, contraction_type, range_of_motion, characteristic_type
    ),
    CONSTRAINT ck_exercise_movement_plane CHECK (
        movement_plane IN ('SAGITTAL', 'FRONTAL', 'TRANSVERSE', 'MULTIPLANAR')
    ),
    CONSTRAINT ck_exercise_contraction_type CHECK (
        contraction_type IN ('CONCENTRIC', 'ECCENTRIC', 'ISOMETRIC', 'MIXED')
    ),
    CONSTRAINT ck_exercise_range_of_motion CHECK (range_of_motion IN ('PARTIAL', 'FULL', 'VARIABLE')),
    CONSTRAINT ck_exercise_characteristic_type CHECK (
        characteristic_type IN (
            'DYNAMIC', 'ISOMETRIC', 'ECCENTRIC_EMPHASIS', 'IMPACT',
            'COMPRESSION', 'SHEAR', 'ROTATION', 'STABILIZATION'
        )
    )
);
CREATE INDEX ix_exercise_load_characteristic_version
    ON exercise_catalog.exercise_load_characteristic (exercise_version_id);

CREATE TABLE exercise_catalog.evidence_source (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL
        REFERENCES exercise_catalog.exercise_version (id) ON DELETE CASCADE,
    citation VARCHAR(500) NOT NULL,
    source_uri VARCHAR(1000),
    evidence_grade VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL
);
CREATE INDEX ix_evidence_source_version
    ON exercise_catalog.evidence_source (exercise_version_id);

CREATE TABLE exercise_catalog.exercise_contribution (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL
        REFERENCES exercise_catalog.exercise_version (id) ON DELETE CASCADE,
    anatomical_structure_id UUID NOT NULL,
    contribution_role VARCHAR(24) NOT NULL,
    load_channel VARCHAR(40) NOT NULL,
    contribution_band VARCHAR(24) NOT NULL,
    coefficient_low NUMERIC(9, 6) NOT NULL,
    coefficient_high NUMERIC(9, 6) NOT NULL,
    confidence_class VARCHAR(80) NOT NULL,
    evidence_grade VARCHAR(80) NOT NULL,
    calculation_role VARCHAR(32) NOT NULL,
    variant_condition VARCHAR(120),
    side_rule VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    CONSTRAINT ck_exercise_contribution_role
        CHECK (contribution_role IN ('PRIMARY', 'SECONDARY', 'STABILIZER')),
    CONSTRAINT ck_exercise_load_channel
        CHECK (load_channel IN ('DYN_EXU', 'ISO_SEC', 'IMPACT_CONTACTS', 'ENDURANCE_MIN_ZONE')),
    CONSTRAINT ck_exercise_contribution_band CHECK (contribution_band IN ('LOW', 'MODERATE', 'HIGH')),
    CONSTRAINT ck_exercise_contribution_interval CHECK (
        coefficient_low >= 0 AND coefficient_low <= coefficient_high AND coefficient_high <= 1
    ),
    CONSTRAINT ck_exercise_calculation_role CHECK (calculation_role IN ('ALLOCATION', 'DESCRIPTIVE_ONLY')),
    CONSTRAINT ck_exercise_contribution_side_rule CHECK (
        side_rule IN ('AS_PRESCRIBED', 'BILATERAL', 'LEFT', 'RIGHT', 'NOT_APPLICABLE')
    )
);
CREATE INDEX ix_exercise_contribution_version
    ON exercise_catalog.exercise_contribution (exercise_version_id);
CREATE INDEX ix_exercise_contribution_structure_channel
    ON exercise_catalog.exercise_contribution (anatomical_structure_id, load_channel);

CREATE TABLE exercise_catalog.exercise_contribution_evidence (
    id UUID PRIMARY KEY,
    contribution_id UUID NOT NULL
        REFERENCES exercise_catalog.exercise_contribution (id) ON DELETE CASCADE,
    evidence_source_id UUID NOT NULL
        REFERENCES exercise_catalog.evidence_source (id) ON DELETE CASCADE,
    CONSTRAINT uq_exercise_contribution_evidence UNIQUE (contribution_id, evidence_source_id)
);
CREATE INDEX ix_exercise_contribution_evidence_source
    ON exercise_catalog.exercise_contribution_evidence (evidence_source_id);
