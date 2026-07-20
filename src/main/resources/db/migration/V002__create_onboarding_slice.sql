ALTER TABLE identity_access.principal_account
    ADD COLUMN profile_type VARCHAR(32),
    ADD CONSTRAINT ck_principal_account_profile_type
        CHECK (profile_type IS NULL OR profile_type IN ('PARTICIPANT', 'SPECIALIST'));

CREATE SCHEMA participant;
CREATE SCHEMA specialist;
CREATE SCHEMA consent;
CREATE SCHEMA availability;

CREATE TABLE participant.participant_profile (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE REFERENCES identity_access.principal_account (id),
    display_name VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE specialist.specialist_profile (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE REFERENCES identity_access.principal_account (id),
    display_name VARCHAR(80) NOT NULL,
    specialist_kind VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_specialist_profile_kind
        CHECK (specialist_kind IN ('TRAINER', 'PHYSIOTHERAPIST'))
);

CREATE TABLE consent.legal_acknowledgement (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    acknowledgement_type VARCHAR(64) NOT NULL,
    document_version VARCHAR(64) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_legal_acknowledgement
        UNIQUE (account_id, acknowledgement_type, document_version),
    CONSTRAINT ck_legal_acknowledgement_type
        CHECK (acknowledgement_type IN ('TERMS_OF_SERVICE', 'PRIVACY_NOTICE'))
);
CREATE INDEX ix_legal_acknowledgement_account
    ON consent.legal_acknowledgement (account_id);

CREATE TABLE availability.recurring_slot (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    day_of_week VARCHAR(16) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recurring_slot
        UNIQUE (account_id, day_of_week, start_time, end_time, time_zone),
    CONSTRAINT ck_recurring_slot_day
        CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_recurring_slot_time CHECK (end_time > start_time)
);
CREATE INDEX ix_recurring_slot_account
    ON availability.recurring_slot (account_id);
