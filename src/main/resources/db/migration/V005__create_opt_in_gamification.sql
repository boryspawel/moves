CREATE SCHEMA gamification;

CREATE TABLE gamification.point_rule_version (
    id UUID PRIMARY KEY,
    version_name VARCHAR(80) NOT NULL UNIQUE,
    base_points INTEGER NOT NULL,
    daily_limit INTEGER NOT NULL,
    weekly_limit INTEGER NOT NULL,
    cooldown_seconds INTEGER NOT NULL,
    repeat_window_days INTEGER NOT NULL,
    full_reward_occurrences INTEGER NOT NULL,
    reduced_reward_percent INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    published_by_subject VARCHAR(255) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_point_rule_values CHECK (
        base_points > 0 AND daily_limit > 0 AND weekly_limit >= daily_limit
        AND cooldown_seconds >= 0 AND repeat_window_days > 0
        AND full_reward_occurrences > 0
        AND reduced_reward_percent BETWEEN 1 AND 100)
);
CREATE UNIQUE INDEX uq_active_point_rule
    ON gamification.point_rule_version (active) WHERE active;

CREATE TABLE gamification.gamification_profile (
    account_id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    pseudonym VARCHAR(80),
    ranking_visible BOOLEAN NOT NULL,
    enabled_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_gamification_profile_enabled_at
        CHECK ((enabled AND enabled_at IS NOT NULL) OR (NOT enabled)),
    CONSTRAINT ck_ranking_requires_enabled
        CHECK (NOT ranking_visible OR enabled)
);
CREATE UNIQUE INDEX uq_gamification_pseudonym
    ON gamification.gamification_profile (lower(pseudonym)) WHERE pseudonym IS NOT NULL;

CREATE TABLE gamification.point_ledger_entry (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    source_execution_id UUID NOT NULL,
    activity_key VARCHAR(2000) NOT NULL,
    rule_version_id UUID NOT NULL REFERENCES gamification.point_rule_version (id),
    entry_type VARCHAR(24) NOT NULL,
    points INTEGER NOT NULL,
    reason VARCHAR(64) NOT NULL,
    explanation VARCHAR(500),
    reverses_entry_id UUID REFERENCES gamification.point_ledger_entry (id),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    CONSTRAINT uq_point_award_source UNIQUE (source_execution_id, entry_type),
    CONSTRAINT uq_point_reversal UNIQUE (reverses_entry_id),
    CONSTRAINT ck_point_ledger_entry_type CHECK (entry_type IN ('AWARD', 'REVERSAL')),
    CONSTRAINT ck_point_ledger_value CHECK (
        (entry_type = 'AWARD' AND points >= 0 AND reverses_entry_id IS NULL)
        OR (entry_type = 'REVERSAL' AND points < 0 AND reverses_entry_id IS NOT NULL)),
    CONSTRAINT ck_point_ledger_reason CHECK (reason IN (
        'SESSION_COMPLETION', 'DIMINISHING_RETURN', 'DAILY_LIMIT',
        'WEEKLY_LIMIT', 'COOLDOWN', 'REVERSAL'))
);
CREATE INDEX ix_point_ledger_account_time
    ON gamification.point_ledger_entry (account_id, occurred_at DESC);
CREATE INDEX ix_point_ledger_activity_time
    ON gamification.point_ledger_entry (account_id, activity_key, occurred_at DESC);

CREATE TABLE gamification.ranking_projection (
    account_id UUID PRIMARY KEY,
    pseudonym VARCHAR(80) NOT NULL,
    points BIGINT NOT NULL,
    rebuilt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ranking_projection_score
    ON gamification.ranking_projection (points DESC, pseudonym);
