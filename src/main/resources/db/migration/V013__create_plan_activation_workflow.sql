ALTER TABLE training_planning.plan_revision
    DROP CONSTRAINT ck_plan_revision_status,
    ADD COLUMN validation_checksum VARCHAR(64),
    ADD COLUMN load_snapshot_id UUID,
    ADD COLUMN safety_assessment_id UUID,
    ADD COLUMN workflow_validated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_plan_revision_status_v3 CHECK (
        status IN ('DRAFT', 'VALIDATING', 'READY', 'NEEDS_REVIEW', 'BLOCKED',
                   'ACTIVE', 'SUPERSEDED', 'COMPLETED')
    );

-- V2 revisions intentionally repeat cycle sequence numbers within one plan.
ALTER TABLE training_planning.training_cycle
    DROP CONSTRAINT uq_training_cycle_sequence;

ALTER TABLE training_planning.plan_revision
    DROP CONSTRAINT ck_plan_revision_assessment_status,
    ADD CONSTRAINT ck_plan_revision_assessment_status_v2 CHECK (
        assessment_status IN ('NOT_ASSESSED', 'PENDING', 'PASS', 'INFO', 'WARNING', 'HARD_BLOCK')
    );

CREATE TABLE training_planning.plan_warning_acknowledgement (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES training_planning.plan_revision (id),
    assessment_id UUID NOT NULL,
    factor_id UUID NOT NULL,
    actor_account_id UUID NOT NULL,
    actor_capability VARCHAR(80) NOT NULL,
    rationale VARCHAR(500) NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_plan_warning_ack UNIQUE (revision_id, assessment_id, factor_id, actor_account_id)
);

CREATE TABLE training_planning.plan_activation_request (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES training_planning.plan_revision (id),
    idempotency_key VARCHAR(120) NOT NULL,
    actor_account_id UUID NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_plan_activation_idempotency UNIQUE (revision_id, idempotency_key)
);

CREATE TABLE audit.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publication_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_outbox_event_id UNIQUE (id)
);
CREATE INDEX ix_outbox_unpublished ON audit.outbox_event (occurred_at) WHERE published_at IS NULL;
