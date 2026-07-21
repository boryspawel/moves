CREATE TABLE identity_access.account_domain_profile (
    account_id UUID NOT NULL REFERENCES identity_access.principal_account (id) ON DELETE CASCADE,
    profile_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, profile_type),
    CONSTRAINT ck_account_domain_profile_type CHECK (profile_type IN ('PARTICIPANT', 'SPECIALIST')),
    CONSTRAINT ck_account_domain_profile_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
INSERT INTO identity_access.account_domain_profile (account_id, profile_type, status, created_at)
SELECT id, profile_type, 'ACTIVE', created_at FROM identity_access.principal_account
WHERE profile_type IS NOT NULL;

CREATE TABLE specialist.professional_scope (
    specialist_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id) ON DELETE CASCADE,
    scope_type VARCHAR(32) NOT NULL,
    verification_status VARCHAR(24) NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (specialist_account_id, scope_type),
    CONSTRAINT ck_professional_scope_type CHECK (scope_type IN ('TRAINER', 'PHYSIOTHERAPIST')),
    CONSTRAINT ck_professional_scope_verification CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')
    )
);
INSERT INTO specialist.professional_scope
    (specialist_account_id, scope_type, verification_status, verified_at, created_at)
SELECT account_id, specialist_kind, 'VERIFIED', updated_at, created_at
FROM specialist.specialist_profile;

CREATE TABLE consent.consent_template_version (
    id UUID PRIMARY KEY,
    template_code VARCHAR(80) NOT NULL,
    version_number INTEGER NOT NULL,
    content_reference VARCHAR(500) NOT NULL,
    legal_basis VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_consent_template_version UNIQUE (template_code, version_number),
    CONSTRAINT ck_consent_template_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN'))
);

CREATE TABLE consent.consent_grant (
    id UUID PRIMARY KEY,
    grantor_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    recipient_type VARCHAR(24) NOT NULL,
    recipient_id UUID NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    template_version_id UUID NOT NULL REFERENCES consent.consent_template_version (id),
    status VARCHAR(16) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_consent_grant_recipient CHECK (recipient_type IN ('SPECIALIST', 'RELATIONSHIP', 'ORGANIZATION')),
    CONSTRAINT ck_consent_grant_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_consent_grant_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_consent_grant_revocation CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL) OR status <> 'REVOKED'
    )
);
CREATE INDEX ix_consent_grant_decision ON consent.consent_grant
    (participant_account_id, recipient_type, recipient_id, purpose, status, valid_from, valid_to);

CREATE TABLE consent.consent_grant_scope (
    grant_id UUID NOT NULL REFERENCES consent.consent_grant (id) ON DELETE CASCADE,
    data_scope VARCHAR(64) NOT NULL,
    PRIMARY KEY (grant_id, data_scope)
);
