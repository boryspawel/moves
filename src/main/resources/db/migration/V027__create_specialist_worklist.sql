CREATE TABLE specialist.worklist_item (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    plan_revision_id UUID,
    category VARCHAR(48) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    minimal_data VARCHAR(500) NOT NULL,
    policy_version_code VARCHAR(80) NOT NULL,
    deduplication_key VARCHAR(240) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    snoozed_until TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    usefulness_outcome VARCHAR(40),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_specialist_worklist_item_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_specialist_worklist_item_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED', 'RESOLVED'))
);
CREATE INDEX ix_specialist_worklist_item_participant_status
    ON specialist.worklist_item (participant_account_id, status, updated_at DESC);

CREATE TABLE specialist.participant_issue (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    worklist_item_id UUID NOT NULL UNIQUE REFERENCES specialist.worklist_item (id),
    problem_code VARCHAR(48) NOT NULL,
    short_text VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE specialist.participant_issue_reply (
    id UUID PRIMARY KEY,
    participant_issue_id UUID NOT NULL REFERENCES specialist.participant_issue (id),
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    short_text VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
