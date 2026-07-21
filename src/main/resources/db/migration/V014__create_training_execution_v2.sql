ALTER TABLE training_execution.session_execution
    ADD COLUMN declaration_event_id UUID,
    ADD COLUMN projection_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT uq_execution_declaration_event UNIQUE (declaration_event_id),
    ADD CONSTRAINT ck_execution_projection_status
        CHECK (projection_status IN ('PENDING', 'PROJECTED', 'FAILED'));

ALTER TABLE training_execution.exercise_result
    ADD COLUMN exercise_version_id UUID,
    ADD COLUMN actual_sets INTEGER,
    ADD COLUMN actual_contacts INTEGER,
    ADD COLUMN actual_distance_meters NUMERIC(12, 2),
    ADD COLUMN actual_external_load_value NUMERIC(12, 2),
    ADD COLUMN actual_external_load_unit VARCHAR(24),
    ADD COLUMN actual_intensity_type VARCHAR(32),
    ADD COLUMN actual_intensity_value NUMERIC(8, 2),
    ADD COLUMN actual_intensity_zone VARCHAR(40),
    ADD COLUMN side VARCHAR(24),
    ADD COLUMN modified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN skipped BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN observation_mode VARCHAR(24) NOT NULL DEFAULT 'DECLARED',
    ADD CONSTRAINT ck_execution_result_sets CHECK (actual_sets IS NULL OR actual_sets >= 0),
    ADD CONSTRAINT ck_execution_result_contacts CHECK (actual_contacts IS NULL OR actual_contacts >= 0),
    ADD CONSTRAINT ck_execution_result_distance CHECK (
        actual_distance_meters IS NULL OR actual_distance_meters >= 0),
    ADD CONSTRAINT ck_execution_result_external_load CHECK (
        actual_external_load_value IS NULL OR actual_external_load_value >= 0),
    ADD CONSTRAINT ck_execution_result_side CHECK (
        side IS NULL OR side IN ('LEFT', 'RIGHT', 'BILATERAL', 'NOT_APPLICABLE')),
    ADD CONSTRAINT ck_execution_result_observation_mode CHECK (
        observation_mode IN ('DECLARED', 'DEVICE', 'ESTIMATED')),
    ADD CONSTRAINT ck_execution_result_skip CHECK (
        NOT skipped OR (COALESCE(actual_sets, 0) = 0 AND COALESCE(actual_repetitions, 0) = 0
            AND COALESCE(actual_duration_seconds, 0) = 0 AND COALESCE(actual_contacts, 0) = 0));

ALTER TABLE training_execution.pain_difficulty_report
    ADD COLUMN session_rpe INTEGER,
    ADD COLUMN observation_mode VARCHAR(24) NOT NULL DEFAULT 'DECLARED',
    ADD CONSTRAINT ck_execution_session_rpe CHECK (session_rpe IS NULL OR session_rpe BETWEEN 1 AND 10),
    ADD CONSTRAINT ck_execution_response_observation_mode CHECK (
        observation_mode IN ('DECLARED', 'DEVICE', 'ESTIMATED'));

ALTER TABLE training_execution.execution_correction
    ADD COLUMN idempotency_key VARCHAR(120),
    ADD COLUMN corrected_result_id UUID,
    ADD COLUMN corrected_sets INTEGER,
    ADD COLUMN corrected_repetitions INTEGER,
    ADD COLUMN corrected_duration_seconds INTEGER,
    ADD COLUMN corrected_contacts INTEGER,
    ADD COLUMN corrected_external_load_value NUMERIC(12, 2),
    ADD COLUMN corrected_external_load_unit VARCHAR(24),
    ADD COLUMN corrected_side VARCHAR(24),
    ADD COLUMN corrected_modified BOOLEAN,
    ADD COLUMN corrected_skipped BOOLEAN,
    ADD COLUMN observation_mode VARCHAR(24) NOT NULL DEFAULT 'DECLARED',
    ADD CONSTRAINT uq_execution_correction_idempotency
        UNIQUE (session_execution_id, idempotency_key),
    ADD CONSTRAINT ck_correction_observation_mode CHECK (
        observation_mode IN ('DECLARED', 'DEVICE', 'ESTIMATED')),
    ADD CONSTRAINT ck_correction_actual_values CHECK (
        (corrected_sets IS NULL OR corrected_sets >= 0)
        AND (corrected_repetitions IS NULL OR corrected_repetitions >= 0)
        AND (corrected_duration_seconds IS NULL OR corrected_duration_seconds >= 0)
        AND (corrected_contacts IS NULL OR corrected_contacts >= 0)
        AND (corrected_external_load_value IS NULL OR corrected_external_load_value >= 0)),
    ADD CONSTRAINT ck_correction_side CHECK (
        corrected_side IS NULL OR corrected_side IN ('LEFT', 'RIGHT', 'BILATERAL', 'NOT_APPLICABLE'));

CREATE TABLE training_execution.post_24h_response (
    id UUID PRIMARY KEY,
    session_execution_id UUID NOT NULL REFERENCES training_execution.session_execution (id),
    participant_account_id UUID NOT NULL,
    pain_level INTEGER NOT NULL,
    difficulty_level INTEGER NOT NULL,
    note VARCHAR(500),
    observation_mode VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_post24h_idempotency UNIQUE (session_execution_id, idempotency_key),
    CONSTRAINT ck_post24h_pain CHECK (pain_level BETWEEN 0 AND 10),
    CONSTRAINT ck_post24h_difficulty CHECK (difficulty_level BETWEEN 1 AND 10),
    CONSTRAINT ck_post24h_observation_mode CHECK (
        observation_mode IN ('DECLARED', 'DEVICE', 'ESTIMATED'))
);

CREATE TABLE training_execution.executed_load_observation (
    id UUID PRIMARY KEY,
    session_execution_id UUID NOT NULL REFERENCES training_execution.session_execution (id),
    result_id UUID NOT NULL REFERENCES training_execution.exercise_result (id),
    exercise_version_id UUID NOT NULL,
    anatomical_structure_id UUID NOT NULL,
    side VARCHAR(24) NOT NULL,
    channel VARCHAR(40) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    value_low NUMERIC(20, 6) NOT NULL,
    value_high NUMERIC(20, 6) NOT NULL,
    observation_mode VARCHAR(24) NOT NULL,
    calculator_version VARCHAR(80) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_executed_observation UNIQUE (session_execution_id, result_id, anatomical_structure_id, channel),
    CONSTRAINT ck_executed_observation_mode CHECK (
        observation_mode IN ('DECLARED', 'DEVICE', 'ESTIMATED'))
);
CREATE INDEX ix_executed_load_participant_window
    ON training_execution.executed_load_observation (session_execution_id, observed_at);

CREATE TABLE training_execution.executed_load_aggregate (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL,
    window_days INTEGER NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    anatomical_structure_id UUID NOT NULL,
    side VARCHAR(24) NOT NULL,
    channel VARCHAR(40) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    value_low NUMERIC(20, 6) NOT NULL,
    value_high NUMERIC(20, 6) NOT NULL,
    rebuilt_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_executed_load_aggregate UNIQUE (
        participant_account_id, window_days, window_end,
        anatomical_structure_id, side, channel, unit),
    CONSTRAINT ck_executed_window CHECK (window_days IN (7, 14, 28))
);

ALTER TABLE training_execution.execution_alert
    DROP CONSTRAINT ck_execution_alert_type,
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN owner_account_id UUID,
    ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN due_at TIMESTAMPTZ,
    ADD COLUMN acknowledged_at TIMESTAMPTZ,
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN source_response_id UUID,
    ADD CONSTRAINT ck_execution_alert_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD CONSTRAINT ck_execution_alert_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'REOPENED'));

CREATE TABLE training_execution.execution_alert_history (
    id UUID PRIMARY KEY,
    alert_id UUID NOT NULL REFERENCES training_execution.execution_alert (id),
    actor_account_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    comment_reference VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_execution_alert_work_queue
    ON training_execution.execution_alert (status, due_at, priority);

CREATE TABLE training_execution.execution_projection_receipt (
    execution_id UUID PRIMARY KEY REFERENCES training_execution.session_execution (id),
    processed_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL
);

CREATE TABLE training_execution.execution_qualification (
    id UUID PRIMARY KEY,
    session_execution_id UUID NOT NULL REFERENCES training_execution.session_execution (id),
    qualification_type VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    reversed_at TIMESTAMPTZ,
    CONSTRAINT uq_execution_qualification UNIQUE (session_execution_id, qualification_type),
    CONSTRAINT ck_execution_qualification_status CHECK (status IN ('QUALIFIED', 'REVERSED'))
);
