ALTER TABLE participant_goals.goal_outcome
    ADD COLUMN target_comparator VARCHAR(16),
    ADD COLUMN measurement_method VARCHAR(120);

ALTER TABLE participant_goals.goal_outcome
    ADD CONSTRAINT uq_participant_goal_outcome_goal_id_id UNIQUE (goal_id, id);

CREATE TABLE participant_goals.goal_observation (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES participant_goals.participant_goal (id) ON DELETE CASCADE,
    outcome_id UUID NOT NULL,
    participant_id UUID NOT NULL REFERENCES participant.participant_record (id),
    value NUMERIC(19,4) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    measurement_method VARCHAR(120),
    measured_at TIMESTAMPTZ NOT NULL,
    note VARCHAR(2000),
    evidence_source VARCHAR(160),
    recorded_by_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_goal_observation_goal_outcome FOREIGN KEY (goal_id, outcome_id)
        REFERENCES participant_goals.goal_outcome (goal_id, id)
);
CREATE INDEX ix_goal_observation_goal_history ON participant_goals.goal_observation
    (goal_id, measured_at DESC, recorded_at DESC, id DESC);
CREATE INDEX ix_goal_observation_goal_outcome_latest ON participant_goals.goal_observation
    (goal_id, outcome_id, measured_at DESC, recorded_at DESC, id DESC);

CREATE TABLE participant_goals.goal_observation_idempotency (
    id UUID PRIMARY KEY,
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    operation VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    observation_id UUID NOT NULL REFERENCES participant_goals.goal_observation (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_goal_observation_idempotency UNIQUE (specialist_account_id, operation, idempotency_key)
);

COMMENT ON TABLE participant_goals.goal_observation IS 'Append-only outcome measurements; never changes participant goal lifecycle.';
