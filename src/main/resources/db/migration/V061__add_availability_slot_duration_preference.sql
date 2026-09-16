CREATE TABLE availability.slot_duration_preference (
    account_id UUID PRIMARY KEY REFERENCES identity_access.principal_account (id) ON DELETE CASCADE,
    slot_duration_minutes INTEGER NOT NULL,
    CONSTRAINT ck_slot_duration_preference_minutes CHECK (slot_duration_minutes BETWEEN 1 AND 480)
);
