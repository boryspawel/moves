CREATE SCHEMA calendar;

CREATE TABLE calendar.appointment (
    id UUID PRIMARY KEY,
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    location_mode VARCHAR(24) NOT NULL,
    location VARCHAR(160),
    short_purpose VARCHAR(500),
    planned_session_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_calendar_appointment_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_calendar_appointment_type CHECK (type IN ('TRAINING', 'PHYSIOTHERAPY', 'ASSESSMENT', 'CONSULTATION')),
    CONSTRAINT ck_calendar_appointment_status CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_calendar_appointment_location_mode CHECK (location_mode IN ('IN_PERSON', 'REMOTE', 'PHONE'))
);
CREATE INDEX ix_calendar_appointment_specialist_time
    ON calendar.appointment (specialist_account_id, starts_at, ends_at);
CREATE INDEX ix_calendar_appointment_specialist_status_time
    ON calendar.appointment (specialist_account_id, status, starts_at);

CREATE TABLE calendar.appointment_idempotency (
    id UUID PRIMARY KEY,
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    operation VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    appointment_id UUID NOT NULL REFERENCES calendar.appointment (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_calendar_appointment_idempotency UNIQUE (specialist_account_id, operation, idempotency_key)
);
