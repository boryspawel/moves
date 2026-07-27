CREATE TABLE participant.participant_record (
    id UUID PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    record_status VARCHAR(16) NOT NULL,
    relationship_context VARCHAR(16) NOT NULL,
    time_zone_id VARCHAR(80),
    email VARCHAR(254),
    phone VARCHAR(40),
    created_by_specialist_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_participant_record_status CHECK (record_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_participant_record_context CHECK (relationship_context IN ('CLIENT', 'PATIENT', 'ATHLETE'))
);

CREATE TABLE participant.participant_access_link (
    id UUID PRIMARY KEY,
    participant_id UUID NOT NULL REFERENCES participant.participant_record (id) ON DELETE CASCADE,
    principal_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    access_status VARCHAR(16) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_participant_access_link_record UNIQUE (participant_id),
    CONSTRAINT uq_participant_access_link_account UNIQUE (principal_account_id),
    CONSTRAINT ck_participant_access_link_status CHECK (access_status IN ('CLAIMED', 'ACTIVE', 'SUSPENDED'))
);

-- Existing profile ids become canonical record ids; legacy account columns remain compatibility bridges.
INSERT INTO participant.participant_record
    (id, display_name, record_status, relationship_context, time_zone_id, created_by_specialist_id, created_at, updated_at, version)
SELECT p.id, p.display_name, 'ACTIVE', 'CLIENT', p.time_zone_id, p.account_id, p.created_at, p.updated_at, p.version
FROM participant.participant_profile p
ON CONFLICT (id) DO NOTHING;

INSERT INTO participant.participant_access_link
    (id, participant_id, principal_account_id, access_status, linked_at, activated_at, version)
SELECT p.id, p.id, p.account_id, 'ACTIVE', p.created_at, p.created_at, p.version
FROM participant.participant_profile p
ON CONFLICT (participant_id) DO NOTHING;

ALTER TABLE specialist.participant_specialist_relationship ADD COLUMN participant_id UUID;
ALTER TABLE specialist.participant_specialist_relationship ADD COLUMN relationship_context VARCHAR(16);
ALTER TABLE specialist.participant_specialist_relationship ALTER COLUMN participant_account_id DROP NOT NULL;
UPDATE specialist.participant_specialist_relationship r
SET participant_id = l.participant_id, relationship_context = 'CLIENT'
FROM participant.participant_access_link l
WHERE r.participant_account_id = l.principal_account_id AND r.participant_id IS NULL;
-- The backfill only obtains ids from participant_access_link, so every non-null value is valid.
ALTER TABLE specialist.participant_specialist_relationship
    ADD CONSTRAINT fk_specialist_relationship_participant_record
    FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_specialist_relationship_participant ON specialist.participant_specialist_relationship (specialist_account_id, participant_id, status);

CREATE TABLE consent.test_default_consent_override (
    id UUID PRIMARY KEY,
    participant_id UUID NOT NULL REFERENCES participant.participant_record (id) ON DELETE CASCADE,
    specialist_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    purpose VARCHAR(64) NOT NULL,
    scopes VARCHAR(500) NOT NULL,
    decision_source VARCHAR(32) NOT NULL,
    accepted_by_participant BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT uq_test_default_consent_override UNIQUE (participant_id, specialist_id, purpose),
    CONSTRAINT ck_test_default_consent_source CHECK (decision_source = 'TEST_DEFAULT')
);

CREATE TABLE specialist.client_create_idempotency (
    specialist_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    idempotency_key UUID NOT NULL,
    participant_id UUID NOT NULL REFERENCES participant.participant_record (id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (specialist_id, idempotency_key)
);
