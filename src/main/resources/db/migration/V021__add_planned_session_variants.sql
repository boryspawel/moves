CREATE TABLE training_planning.planned_session_variant (
    id UUID PRIMARY KEY,
    planned_session_id UUID NOT NULL REFERENCES training_planning.planned_session (id) ON DELETE CASCADE,
    variant_type VARCHAR(16) NOT NULL,
    expected_duration_minutes INTEGER,
    CONSTRAINT uq_planned_session_variant_type UNIQUE (planned_session_id, variant_type),
    CONSTRAINT ck_planned_session_variant_type CHECK (variant_type IN ('STANDARD', 'SHORT', 'MINIMUM')),
    CONSTRAINT ck_planned_session_variant_duration CHECK (
        expected_duration_minutes IS NULL OR expected_duration_minutes > 0
    )
);

CREATE TABLE training_planning.planned_session_variant_item (
    id UUID PRIMARY KEY,
    session_variant_id UUID NOT NULL REFERENCES training_planning.planned_session_variant (id) ON DELETE CASCADE,
    base_prescription_id UUID NOT NULL REFERENCES training_planning.exercise_prescription (id),
    position INTEGER NOT NULL,
    override_sets INTEGER,
    override_repetitions INTEGER,
    override_duration_seconds INTEGER,
    override_contacts INTEGER,
    CONSTRAINT uq_planned_session_variant_position UNIQUE (session_variant_id, position),
    CONSTRAINT uq_planned_session_variant_prescription UNIQUE (session_variant_id, base_prescription_id),
    CONSTRAINT ck_planned_session_variant_position CHECK (position > 0),
    CONSTRAINT ck_planned_session_variant_override_sets CHECK (override_sets IS NULL OR override_sets >= 0),
    CONSTRAINT ck_planned_session_variant_override_repetitions CHECK (override_repetitions IS NULL OR override_repetitions >= 0),
    CONSTRAINT ck_planned_session_variant_override_duration CHECK (override_duration_seconds IS NULL OR override_duration_seconds >= 0),
    CONSTRAINT ck_planned_session_variant_override_contacts CHECK (override_contacts IS NULL OR override_contacts >= 0)
);
