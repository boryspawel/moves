CREATE TABLE training_planning.plan_collaborator (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES training_planning.training_plan (id),
    specialist_account_id UUID NOT NULL,
    professional_role VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    added_by_account_id UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT uq_plan_collaborator UNIQUE (plan_id, specialist_account_id),
    CONSTRAINT ck_plan_collaborator_role CHECK (professional_role IN ('TRAINER', 'PHYSIOTHERAPIST')),
    CONSTRAINT ck_plan_collaborator_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_plan_collaborator_end CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL) OR (status = 'ENDED' AND ended_at IS NOT NULL))
);
CREATE INDEX ix_plan_collaborator_access
    ON training_planning.plan_collaborator (specialist_account_id, status, plan_id);

CREATE TABLE training_planning.plan_collaborator_scope (
    collaborator_id UUID NOT NULL REFERENCES training_planning.plan_collaborator (id) ON DELETE CASCADE,
    scope VARCHAR(32) NOT NULL,
    PRIMARY KEY (collaborator_id, scope),
    CONSTRAINT ck_plan_collaboration_scope CHECK (
        scope IN ('VIEW_PLAN', 'EDIT_DRAFT', 'REVIEW_SAFETY'))
);

CREATE TABLE training_planning.plan_review_request (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES training_planning.plan_revision (id),
    requested_by_account_id UUID NOT NULL,
    reviewer_account_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_reference VARCHAR(500) NOT NULL,
    decision_reference VARCHAR(500),
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    CONSTRAINT ck_plan_review_status CHECK (
        status IN ('OPEN', 'CHANGE_PROPOSED', 'READY_FOR_REVALIDATION', 'CANCELLED')),
    CONSTRAINT ck_plan_review_decision CHECK (
        (status = 'OPEN' AND decision_reference IS NULL AND decided_at IS NULL)
        OR (status <> 'OPEN' AND decision_reference IS NOT NULL AND decided_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_open_plan_review
    ON training_planning.plan_review_request (revision_id, reviewer_account_id)
    WHERE status = 'OPEN';
CREATE INDEX ix_plan_review_reviewer_status
    ON training_planning.plan_review_request (reviewer_account_id, status, requested_at);
