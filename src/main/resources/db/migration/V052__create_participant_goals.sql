CREATE SCHEMA participant_goals;

CREATE TABLE participant_goals.participant_goal (
    id UUID PRIMARY KEY,
    participant_id UUID NOT NULL REFERENCES participant.participant_record (id),
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    category VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    priority INTEGER NOT NULL,
    target_date DATE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    achieved_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_participant_goal_category CHECK (category IN ('PERFORMANCE', 'FUNCTIONAL', 'GENERAL_FITNESS')),
    CONSTRAINT ck_participant_goal_priority CHECK (priority BETWEEN 1 AND 100),
    CONSTRAINT ck_participant_goal_status CHECK (status IN ('ACTIVE', 'ACHIEVED', 'CANCELLED'))
);
CREATE INDEX ix_participant_goal_specialist_participant_status ON participant_goals.participant_goal (specialist_account_id, participant_id, status, priority DESC, target_date, created_at, id);

CREATE TABLE participant_goals.goal_outcome (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES participant_goals.participant_goal (id) ON DELETE CASCADE,
    metric_code VARCHAR(80) NOT NULL,
    target_value NUMERIC(19,4) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_participant_goal_outcome_metric UNIQUE (goal_id, metric_code),
    CONSTRAINT uq_participant_goal_outcome_position UNIQUE (goal_id, position),
    CONSTRAINT ck_participant_goal_outcome_position CHECK (position >= 0)
);

CREATE TABLE participant_goals.goal_idempotency (
    id UUID PRIMARY KEY,
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    goal_id UUID NOT NULL REFERENCES participant_goals.participant_goal (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_participant_goal_idempotency UNIQUE (specialist_account_id, operation, idempotency_key)
);

COMMENT ON TABLE participant_goals.participant_goal IS 'Participant-owned outcome goals; not training-planning goals and never automatically achieved.';
