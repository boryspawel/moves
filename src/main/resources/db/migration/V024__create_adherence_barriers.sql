CREATE SCHEMA IF NOT EXISTS adherence;

CREATE TABLE adherence.barrier_rule_version (
    code VARCHAR(80) PRIMARY KEY,
    version INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_barrier_rule_version_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);

INSERT INTO adherence.barrier_rule_version (code, version, status, published_at)
VALUES ('BARRIER_RESPONSE_V1', 1, 'ACTIVE', now());

CREATE TABLE adherence.barrier_report (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL,
    planned_session_id UUID NOT NULL,
    session_attempt_id UUID,
    plan_revision_id UUID NOT NULL,
    category VARCHAR(32) NOT NULL,
    rule_version_code VARCHAR(80) NOT NULL REFERENCES adherence.barrier_rule_version (code),
    proposed_options VARCHAR(500) NOT NULL,
    selected_action VARCHAR(40),
    action_outcome VARCHAR(80),
    idempotency_key VARCHAR(120) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_barrier_report_idempotency UNIQUE (participant_account_id, idempotency_key),
    CONSTRAINT ck_barrier_report_category CHECK (category IN (
        'NO_TIME', 'PAIN_OR_SYMPTOMS', 'TOO_DIFFICULT', 'UNSURE_TECHNIQUE',
        'FATIGUE', 'ILLNESS', 'LOGISTICS', 'LOW_MOTIVATION', 'OTHER'))
);
CREATE INDEX ix_barrier_report_participant_session
    ON adherence.barrier_report (participant_account_id, planned_session_id, reported_at DESC);

CREATE TABLE specialist.adherence_contact_signal (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL,
    barrier_report_id UUID NOT NULL UNIQUE REFERENCES adherence.barrier_report (id),
    category VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_adherence_contact_signal_priority CHECK (priority IN ('ROUTINE', 'PROMPT')),
    CONSTRAINT ck_adherence_contact_signal_status CHECK (status IN ('OPEN', 'RESOLVED'))
);
