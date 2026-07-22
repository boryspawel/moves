CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.adherence_experiment_assignment (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    experiment_key VARCHAR(80) NOT NULL,
    experiment_version INTEGER NOT NULL,
    variant_code VARCHAR(48) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_adherence_experiment_assignment UNIQUE (participant_account_id, experiment_key, experiment_version)
);

CREATE TABLE analytics.adherence_metric_event (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    event_code VARCHAR(80) NOT NULL,
    technical_reference_id UUID,
    plan_revision_id UUID,
    planned_session_id UUID,
    session_attempt_id UUID,
    rule_version_code VARCHAR(80),
    variant_code VARCHAR(48),
    deduplication_key VARCHAR(240) NOT NULL UNIQUE,
    occurred_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_adherence_metric_event_code CHECK (event_code IN (
        'PLAN_ACTIVATED', 'SESSION_ATTEMPT_STARTED', 'SESSION_COMPLETED', 'BARRIER_REPORTED',
        'RECOVERY_CHOICE_SELECTED', 'RECOVERY_RETURN_COMPLETED', 'AVAILABILITY_REPLACED',
        'WORKLIST_REPLIED'))
);
CREATE INDEX ix_adherence_metric_event_expiry ON analytics.adherence_metric_event (expires_at);
