CREATE TABLE training_execution.session_execution_attempt (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL,
    planned_session_id UUID NOT NULL,
    plan_revision_id UUID,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    paused_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    abandoned_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_session_execution_attempt_status
        CHECK (status IN ('STARTED', 'PAUSED', 'COMPLETED', 'ABANDONED'))
);
CREATE UNIQUE INDEX uq_session_execution_attempt_active
    ON training_execution.session_execution_attempt (participant_account_id, planned_session_id)
    WHERE status IN ('STARTED', 'PAUSED');
CREATE INDEX ix_session_execution_attempt_progress
    ON training_execution.session_execution_attempt (participant_account_id, planned_session_id, updated_at DESC);
