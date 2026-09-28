ALTER TABLE calendar.appointment_event
    DROP CONSTRAINT ck_calendar_appointment_event_type,
    ADD CONSTRAINT ck_calendar_appointment_event_type
        CHECK (event_type IN ('CREATED', 'UPDATED', 'RESCHEDULED', 'STARTED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'BASELINE'));

CREATE UNIQUE INDEX uq_calendar_appointment_planned_session
    ON calendar.appointment (planned_session_id)
    WHERE planned_session_id IS NOT NULL;
