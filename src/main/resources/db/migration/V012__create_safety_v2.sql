CREATE TABLE safety.restriction (
    id UUID PRIMARY KEY,
    root_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    supersedes_restriction_id UUID REFERENCES safety.restriction (id),
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    source_type VARCHAR(32) NOT NULL,
    semantic_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    author_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    author_capability VARCHAR(64) NOT NULL,
    participant_explanation VARCHAR(500) NOT NULL,
    clinical_rationale_ref VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_restriction_revision UNIQUE (root_id, revision_number),
    CONSTRAINT ck_restriction_source CHECK (
        source_type IN ('PARTICIPANT_DECLARED', 'PHYSIOTHERAPIST', 'SYSTEM_OPERATIONAL')),
    CONSTRAINT ck_restriction_semantic CHECK (
        semantic_type IN ('CONTRAINDICATION', 'CAUTION', 'LIMIT', 'MONITOR')),
    CONSTRAINT ck_restriction_status CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT ck_restriction_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_restriction_active_participant
    ON safety.restriction (participant_account_id, status, valid_from, valid_to);

CREATE TABLE safety.restriction_target (
    restriction_id UUID PRIMARY KEY REFERENCES safety.restriction (id) ON DELETE CASCADE,
    structure_id UUID,
    movement_pattern VARCHAR(80),
    channel VARCHAR(40),
    load_characteristic VARCHAR(40),
    side VARCHAR(24),
    range_of_motion VARCHAR(40),
    contraction_type VARCHAR(40),
    limit_low NUMERIC(20, 6),
    limit_high NUMERIC(20, 6),
    unit VARCHAR(24),
    minimum_recovery_hours INTEGER,
    CONSTRAINT ck_restriction_target_range CHECK (
        limit_low IS NULL OR limit_high IS NULL OR limit_low <= limit_high),
    CONSTRAINT ck_restriction_target_recovery CHECK (
        minimum_recovery_hours IS NULL OR minimum_recovery_hours > 0)
);

CREATE TABLE safety.safety_rule_version (
    ruleset_code VARCHAR(80) NOT NULL,
    version_number INTEGER NOT NULL,
    strategy_codes VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (ruleset_code, version_number),
    CONSTRAINT ck_safety_rule_status CHECK (status IN ('PUBLISHED', 'WITHDRAWN'))
);
INSERT INTO safety.safety_rule_version
    (ruleset_code, version_number, strategy_codes, status, published_at)
VALUES
    ('SAFETY_V2', 1,
     'HARD_RESTRICTION_INTERSECTION,EXPLICIT_LIMIT,MINIMUM_RECOVERY,PARTICIPANT_DECLARATION,LOW_CONFIDENCE_MAPPING',
     'PUBLISHED', now());

CREATE TABLE safety.plan_safety_assessment (
    id UUID PRIMARY KEY,
    participant_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    revision_id UUID NOT NULL,
    load_snapshot_id UUID NOT NULL,
    load_input_checksum VARCHAR(64) NOT NULL,
    load_calculation_version VARCHAR(180) NOT NULL,
    ruleset_code VARCHAR(80) NOT NULL,
    ruleset_version INTEGER NOT NULL,
    result VARCHAR(16) NOT NULL,
    restriction_snapshot TEXT NOT NULL,
    ruleset_snapshot TEXT NOT NULL,
    load_snapshot TEXT NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_safety_assessment_result CHECK (result IN ('PASS', 'INFO', 'WARNING', 'HARD_BLOCK')),
    CONSTRAINT fk_safety_assessment_ruleset FOREIGN KEY (ruleset_code, ruleset_version)
        REFERENCES safety.safety_rule_version (ruleset_code, version_number)
);
CREATE INDEX ix_safety_assessment_revision
    ON safety.plan_safety_assessment (revision_id, assessed_at DESC);

CREATE TABLE safety.assessment_factor (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES safety.plan_safety_assessment (id) ON DELETE CASCADE,
    result VARCHAR(16) NOT NULL,
    rule_code VARCHAR(80) NOT NULL,
    target_ref VARCHAR(500) NOT NULL,
    channel VARCHAR(40),
    observed_low NUMERIC(20, 6),
    observed_high NUMERIC(20, 6),
    threshold_low NUMERIC(20, 6),
    threshold_high NUMERIC(20, 6),
    unit VARCHAR(24),
    explanation_code VARCHAR(100) NOT NULL,
    evidence_grade VARCHAR(40) NOT NULL,
    overridable BOOLEAN NOT NULL
);

CREATE TABLE safety.assessment_override (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES safety.plan_safety_assessment (id),
    factor_id UUID NOT NULL REFERENCES safety.assessment_factor (id),
    actor_account_id UUID NOT NULL REFERENCES identity_access.principal_account (id),
    actor_capability VARCHAR(64) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    scope VARCHAR(160) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_safety_override_dates CHECK (valid_to >= valid_from)
);
CREATE INDEX ix_safety_override_factor
    ON safety.assessment_override (factor_id, valid_from, valid_to);
