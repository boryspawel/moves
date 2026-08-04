-- Appointment is the current-state snapshot. This append-only table supplies lifecycle history;
-- BASELINE rows represent the state observed at migration time and are not domain events.
CREATE TABLE calendar.appointment_event (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES calendar.appointment (id),
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    participant_id UUID NOT NULL REFERENCES participant.participant_record (id),
    event_type VARCHAR(24) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    actor_account_id UUID REFERENCES identity_access.principal_account (id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    type VARCHAR(24) NOT NULL,
    location_mode VARCHAR(24) NOT NULL,
    location VARCHAR(160),
    short_purpose VARCHAR(500),
    previous_starts_at TIMESTAMPTZ,
    previous_ends_at TIMESTAMPTZ,
    CONSTRAINT ck_calendar_appointment_event_type CHECK (event_type IN ('CREATED', 'UPDATED', 'RESCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'BASELINE')),
    CONSTRAINT ck_calendar_appointment_event_from_status CHECK (from_status IS NULL OR from_status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_calendar_appointment_event_to_status CHECK (to_status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_calendar_appointment_event_snapshot_time CHECK (ends_at > starts_at)
);

CREATE INDEX ix_calendar_appointment_event_specialist_participant_effective
    ON calendar.appointment_event (specialist_account_id, participant_id, effective_at DESC, recorded_at DESC, id ASC);

INSERT INTO calendar.appointment_event (
    id, appointment_id, specialist_account_id, participant_id, event_type, from_status, to_status,
    effective_at, recorded_at, actor_account_id, starts_at, ends_at, type, location_mode, location, short_purpose,
    previous_starts_at, previous_ends_at
)
SELECT gen_random_uuid(), a.id, a.specialist_account_id, a.participant_id, 'BASELINE', NULL, a.status,
       a.starts_at, a.updated_at, a.created_by_account_id, a.starts_at, a.ends_at, a.type, a.location_mode, a.location, a.short_purpose,
       NULL, NULL
FROM calendar.appointment a;
