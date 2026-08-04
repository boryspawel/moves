ALTER TABLE calendar.appointment_event
    ALTER COLUMN event_type TYPE VARCHAR(64),
    ALTER COLUMN from_status TYPE VARCHAR(64),
    ALTER COLUMN to_status TYPE VARCHAR(64),
    ALTER COLUMN type TYPE VARCHAR(64),
    ALTER COLUMN location_mode TYPE VARCHAR(64);
