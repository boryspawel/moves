CREATE TABLE exercise_set.exercise_set_version_grant (
    id UUID PRIMARY KEY,
    exercise_set_version_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    granted_by_account_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_by_account_id UUID,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_exercise_set_version_grant_recipient UNIQUE (exercise_set_version_id, participant_id)
);

CREATE INDEX ix_exercise_set_version_grant_available
    ON exercise_set.exercise_set_version_grant (participant_id, exercise_set_version_id)
    WHERE revoked_at IS NULL;
