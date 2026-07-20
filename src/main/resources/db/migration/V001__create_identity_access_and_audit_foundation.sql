CREATE SCHEMA identity_access;
CREATE SCHEMA audit;

CREATE TABLE identity_access.principal_account (
    id UUID PRIMARY KEY,
    external_subject VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_principal_account_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETION_PENDING', 'DELETED'))
);

CREATE TABLE audit.audit_event (
    id UUID PRIMARY KEY,
    actor_subject VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100),
    aggregate_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB
);

CREATE INDEX ix_audit_event_occurred_at ON audit.audit_event (occurred_at);
CREATE INDEX ix_audit_event_aggregate ON audit.audit_event (aggregate_type, aggregate_id);
