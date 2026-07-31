CREATE TABLE exercise_set.exercise_set_anatomy_analysis_run (
    id UUID PRIMARY KEY,
    exercise_set_version_id UUID NOT NULL UNIQUE REFERENCES exercise_set.exercise_set_version(id) ON DELETE CASCADE,
    policy_version VARCHAR(48) NOT NULL,
    analyzed_lock_version BIGINT NOT NULL CHECK (analyzed_lock_version >= 0),
    analyzed_at TIMESTAMPTZ NOT NULL,
    result JSONB NOT NULL
);
