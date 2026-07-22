CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.reminder_preference (
    participant_account_id UUID PRIMARY KEY,
    time_zone VARCHAR(64) NOT NULL,
    preferred_window_start TIME NOT NULL,
    preferred_window_end TIME NOT NULL,
    channel VARCHAR(24) NOT NULL,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    max_messages_per_week INTEGER NOT NULL DEFAULT 3,
    reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    gentle_return_consent BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reminder_channel CHECK (channel IN ('IN_APP')),
    CONSTRAINT ck_reminder_frequency CHECK (max_messages_per_week BETWEEN 1 AND 7)
);

CREATE TABLE notification.reminder_rule_version (
    code VARCHAR(80) PRIMARY KEY,
    version INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reminder_rule_version_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);
INSERT INTO notification.reminder_rule_version (code, version, status, published_at)
VALUES ('REMINDER_RULES_V1', 1, 'ACTIVE', now());

CREATE TABLE notification.reminder_delivery (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL,
    planned_session_id UUID,
    reason_code VARCHAR(80) NOT NULL,
    rule_version_code VARCHAR(80) NOT NULL REFERENCES notification.reminder_rule_version(code),
    local_delivery_date DATE NOT NULL,
    channel VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    decision VARCHAR(24) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    CONSTRAINT uq_reminder_delivery_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_reminder_delivery_session_reason_day UNIQUE (participant_account_id, planned_session_id, reason_code, local_delivery_date),
    CONSTRAINT ck_reminder_delivery_decision CHECK (decision IN ('DELIVERED', 'SUPPRESSED'))
);
CREATE INDEX ix_reminder_delivery_participant_time
    ON notification.reminder_delivery (participant_account_id, decided_at DESC);
