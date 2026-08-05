CREATE TABLE participant_goals.participant_goal_event (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES participant_goals.participant_goal (id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES participant.participant_record (id),
    observation_id UUID REFERENCES participant_goals.goal_observation (id),
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    priority INTEGER NOT NULL,
    target_date DATE,
    metric_code VARCHAR(80),
    observation_value NUMERIC(19,4),
    observation_unit VARCHAR(40),
    measured_at TIMESTAMPTZ,
    progress_state VARCHAR(32),
    CONSTRAINT ck_participant_goal_event_type CHECK (event_type IN ('BASELINE', 'CREATED', 'UPDATED', 'OBSERVATION_RECORDED', 'ACHIEVED', 'CANCELLED')),
    CONSTRAINT ck_participant_goal_event_status CHECK (to_status IN ('ACTIVE', 'ACHIEVED', 'CANCELLED'))
);
CREATE INDEX ix_participant_goal_event_timeline ON participant_goals.participant_goal_event (participant_id, effective_at DESC, recorded_at DESC, id);
CREATE INDEX ix_participant_goal_event_goal ON participant_goals.participant_goal_event (goal_id, recorded_at DESC, id);
INSERT INTO participant_goals.participant_goal_event (id, goal_id, participant_id, event_type, from_status, to_status, effective_at, recorded_at, category, title, description, priority, target_date)
SELECT gen_random_uuid(), id, participant_id, 'BASELINE', NULL, status, created_at, updated_at, category, title, description, priority, target_date
FROM participant_goals.participant_goal;
COMMENT ON TABLE participant_goals.participant_goal_event IS 'Append-only snapshots of participant-goal mutations; baseline is current-state migration evidence, not reconstructed history.';
