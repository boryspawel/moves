CREATE SCHEMA training_planning;
CREATE SCHEMA training_execution;

CREATE TABLE specialist.participant_specialist_relationship (
    id UUID PRIMARY KEY,
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    status VARCHAR(24) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT uq_participant_specialist_relationship
        UNIQUE (specialist_account_id, participant_account_id),
    CONSTRAINT ck_participant_specialist_distinct
        CHECK (specialist_account_id <> participant_account_id),
    CONSTRAINT ck_participant_specialist_status
        CHECK (status IN ('ACTIVE', 'ENDED'))
);
CREATE INDEX ix_relationship_participant
    ON specialist.participant_specialist_relationship (participant_account_id, status);

CREATE TABLE training_planning.training_goal (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    created_by_account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE training_planning.training_plan (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES training_planning.training_goal (id),
    participant_account_id UUID NOT NULL,
    created_by_account_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    plan_mode VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_training_plan_mode
        CHECK (plan_mode IN ('SPECIALIST_ASSIGNED', 'SELF_DIRECTED')),
    CONSTRAINT ck_training_plan_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE training_planning.training_cycle (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES training_planning.training_plan (id),
    sequence_number INTEGER NOT NULL,
    name VARCHAR(160) NOT NULL,
    CONSTRAINT uq_training_cycle_sequence UNIQUE (plan_id, sequence_number),
    CONSTRAINT ck_training_cycle_sequence CHECK (sequence_number > 0)
);

CREATE TABLE training_planning.microcycle (
    id UUID PRIMARY KEY,
    cycle_id UUID NOT NULL REFERENCES training_planning.training_cycle (id),
    sequence_number INTEGER NOT NULL,
    name VARCHAR(160) NOT NULL,
    CONSTRAINT uq_microcycle_sequence UNIQUE (cycle_id, sequence_number),
    CONSTRAINT ck_microcycle_sequence CHECK (sequence_number > 0)
);

CREATE TABLE training_planning.planned_session (
    id UUID PRIMARY KEY,
    microcycle_id UUID NOT NULL REFERENCES training_planning.microcycle (id),
    participant_account_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    session_kind VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_planned_session_kind
        CHECK (session_kind IN ('SELF_GUIDED', 'OFFLINE_APPOINTMENT')),
    CONSTRAINT ck_planned_session_status
        CHECK (status IN ('ASSIGNED', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX ix_planned_session_participant
    ON training_planning.planned_session (participant_account_id, assigned_at DESC);

CREATE TABLE training_planning.exercise_prescription (
    id UUID PRIMARY KEY,
    planned_session_id UUID NOT NULL REFERENCES training_planning.planned_session (id),
    exercise_version_id UUID NOT NULL,
    position INTEGER NOT NULL,
    target_sets INTEGER,
    target_repetitions INTEGER,
    target_duration_seconds INTEGER,
    target_load_kg NUMERIC(8, 2),
    notes VARCHAR(500),
    CONSTRAINT uq_exercise_prescription_position UNIQUE (planned_session_id, position),
    CONSTRAINT ck_exercise_prescription_position CHECK (position > 0),
    CONSTRAINT ck_exercise_prescription_sets CHECK (target_sets IS NULL OR target_sets > 0),
    CONSTRAINT ck_exercise_prescription_reps CHECK (target_repetitions IS NULL OR target_repetitions > 0),
    CONSTRAINT ck_exercise_prescription_duration CHECK (target_duration_seconds IS NULL OR target_duration_seconds > 0),
    CONSTRAINT ck_exercise_prescription_load CHECK (target_load_kg IS NULL OR target_load_kg >= 0)
);

CREATE TABLE training_execution.session_execution (
    id UUID PRIMARY KEY,
    planned_session_id UUID NOT NULL,
    participant_account_id UUID NOT NULL,
    declared_completion BOOLEAN NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_session_execution_idempotency
        UNIQUE (participant_account_id, idempotency_key),
    CONSTRAINT ck_session_execution_declared CHECK (declared_completion)
);
CREATE INDEX ix_session_execution_session
    ON training_execution.session_execution (planned_session_id, recorded_at DESC);

CREATE TABLE training_execution.exercise_result (
    id UUID PRIMARY KEY,
    session_execution_id UUID NOT NULL REFERENCES training_execution.session_execution (id),
    exercise_prescription_id UUID NOT NULL,
    actual_repetitions INTEGER,
    actual_duration_seconds INTEGER,
    actual_load_kg NUMERIC(8, 2),
    CONSTRAINT uq_exercise_result_prescription
        UNIQUE (session_execution_id, exercise_prescription_id),
    CONSTRAINT ck_exercise_result_reps CHECK (actual_repetitions IS NULL OR actual_repetitions >= 0),
    CONSTRAINT ck_exercise_result_duration CHECK (actual_duration_seconds IS NULL OR actual_duration_seconds >= 0),
    CONSTRAINT ck_exercise_result_load CHECK (actual_load_kg IS NULL OR actual_load_kg >= 0)
);

CREATE TABLE training_execution.pain_difficulty_report (
    id UUID PRIMARY KEY,
    session_execution_id UUID NOT NULL UNIQUE REFERENCES training_execution.session_execution (id),
    pain_level INTEGER NOT NULL,
    difficulty_level INTEGER NOT NULL,
    note VARCHAR(500),
    reported_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_execution_pain CHECK (pain_level BETWEEN 0 AND 10),
    CONSTRAINT ck_execution_difficulty CHECK (difficulty_level BETWEEN 1 AND 10)
);

CREATE TABLE training_execution.execution_correction (
    id UUID PRIMARY KEY,
    session_execution_id UUID NOT NULL REFERENCES training_execution.session_execution (id),
    corrected_by_account_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    corrected_pain_level INTEGER,
    corrected_difficulty_level INTEGER,
    corrected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_correction_pain
        CHECK (corrected_pain_level IS NULL OR corrected_pain_level BETWEEN 0 AND 10),
    CONSTRAINT ck_correction_difficulty
        CHECK (corrected_difficulty_level IS NULL OR corrected_difficulty_level BETWEEN 1 AND 10)
);
CREATE INDEX ix_execution_correction_history
    ON training_execution.execution_correction (session_execution_id, corrected_at);

CREATE TABLE training_execution.execution_alert (
    id UUID PRIMARY KEY,
    session_execution_id UUID NOT NULL REFERENCES training_execution.session_execution (id),
    alert_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_execution_alert_type UNIQUE (session_execution_id, alert_type),
    CONSTRAINT ck_execution_alert_type CHECK (alert_type IN ('PAIN_REPORTED'))
);
