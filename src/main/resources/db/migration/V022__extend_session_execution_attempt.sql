ALTER TABLE training_execution.session_execution_attempt
    ADD COLUMN selected_variant_type VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN abandonment_reason VARCHAR(80);

ALTER TABLE training_execution.session_execution_attempt
    ADD CONSTRAINT ck_session_execution_attempt_variant
        CHECK (selected_variant_type IN ('STANDARD', 'SHORT', 'MINIMUM'));

CREATE TABLE training_execution.session_execution_attempt_progress (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES training_execution.session_execution_attempt (id) ON DELETE CASCADE,
    exercise_prescription_id UUID NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (attempt_id, exercise_prescription_id)
);

CREATE INDEX ix_session_execution_attempt_progress_attempt
    ON training_execution.session_execution_attempt_progress (attempt_id, updated_at);
