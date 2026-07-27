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

-- participant_id is the canonical participant identity. The account columns below are
-- retained only as nullable legacy bridges for already-claimed participant records.
ALTER TABLE calendar.appointment ADD COLUMN participant_id UUID;
UPDATE calendar.appointment appointment
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE appointment.participant_account_id = link.principal_account_id;
ALTER TABLE calendar.appointment
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_calendar_appointment_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_calendar_appointment_participant_time
    ON calendar.appointment (participant_id, starts_at, ends_at);

ALTER TABLE training_planning.training_goal ADD COLUMN participant_id UUID;
UPDATE training_planning.training_goal goal
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE goal.participant_account_id = link.principal_account_id;
ALTER TABLE training_planning.training_goal
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_training_goal_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_training_goal_participant_created
    ON training_planning.training_goal (participant_id, created_at);

ALTER TABLE training_planning.training_plan ADD COLUMN participant_id UUID;
UPDATE training_planning.training_plan plan
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE plan.participant_account_id = link.principal_account_id;
ALTER TABLE training_planning.training_plan
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_training_plan_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_training_plan_participant_status
    ON training_planning.training_plan (participant_id, status);

ALTER TABLE training_planning.planned_session ADD COLUMN participant_id UUID;
UPDATE training_planning.planned_session session
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE session.participant_account_id = link.principal_account_id;
ALTER TABLE training_planning.planned_session
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_planned_session_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_planned_session_canonical_participant
    ON training_planning.planned_session (participant_id, assigned_at DESC);

ALTER TABLE training_execution.session_execution ADD COLUMN participant_id UUID;
UPDATE training_execution.session_execution execution
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE execution.participant_account_id = link.principal_account_id;
ALTER TABLE training_execution.session_execution
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_session_execution_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE UNIQUE INDEX uq_session_execution_participant_idempotency
    ON training_execution.session_execution (participant_id, idempotency_key);
CREATE INDEX ix_session_execution_participant_recorded
    ON training_execution.session_execution (participant_id, recorded_at DESC);

ALTER TABLE training_execution.session_execution_attempt ADD COLUMN participant_id UUID;
UPDATE training_execution.session_execution_attempt attempt
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE attempt.participant_account_id = link.principal_account_id;
ALTER TABLE training_execution.session_execution_attempt
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_session_execution_attempt_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE UNIQUE INDEX uq_session_execution_attempt_canonical_active
    ON training_execution.session_execution_attempt (participant_id, planned_session_id)
    WHERE status IN ('STARTED', 'PAUSED');
CREATE UNIQUE INDEX uq_session_execution_attempt_canonical_idempotency
    ON training_execution.session_execution_attempt (participant_id, start_idempotency_key)
    WHERE start_idempotency_key <> $$legacy$$;
CREATE INDEX ix_session_execution_attempt_canonical_progress
    ON training_execution.session_execution_attempt (participant_id, planned_session_id, updated_at DESC);

ALTER TABLE training_execution.post_24h_response ADD COLUMN participant_id UUID;
UPDATE training_execution.post_24h_response response
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE response.participant_account_id = link.principal_account_id;
ALTER TABLE training_execution.post_24h_response
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_post_24h_response_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_post_24h_response_participant_reported
    ON training_execution.post_24h_response (participant_id, reported_at DESC);

ALTER TABLE training_execution.executed_load_aggregate ADD COLUMN participant_id UUID;
UPDATE training_execution.executed_load_aggregate aggregate
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE aggregate.participant_account_id = link.principal_account_id;
ALTER TABLE training_execution.executed_load_aggregate
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_executed_load_aggregate_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE UNIQUE INDEX uq_executed_load_aggregate_canonical_participant
    ON training_execution.executed_load_aggregate
        (participant_id, window_days, window_end, anatomical_structure_id, side, channel, unit);

ALTER TABLE specialist.worklist_item ADD COLUMN participant_id UUID;
UPDATE specialist.worklist_item item
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE item.participant_account_id = link.principal_account_id;
ALTER TABLE specialist.worklist_item
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_worklist_item_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_worklist_item_canonical_participant_status
    ON specialist.worklist_item (participant_id, status, updated_at DESC);

ALTER TABLE specialist.participant_issue ADD COLUMN participant_id UUID;
UPDATE specialist.participant_issue issue
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE issue.participant_account_id = link.principal_account_id;
ALTER TABLE specialist.participant_issue
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_participant_issue_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_participant_issue_canonical_participant
    ON specialist.participant_issue (participant_id, created_at DESC);

ALTER TABLE specialist.adherence_contact_signal ADD COLUMN participant_id UUID;
UPDATE specialist.adherence_contact_signal signal
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE signal.participant_account_id = link.principal_account_id;
ALTER TABLE specialist.adherence_contact_signal
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_adherence_contact_signal_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_adherence_contact_signal_participant_status
    ON specialist.adherence_contact_signal (participant_id, status, created_at DESC);

ALTER TABLE adherence.barrier_report ADD COLUMN participant_id UUID;
UPDATE adherence.barrier_report report
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE report.participant_account_id = link.principal_account_id;
ALTER TABLE adherence.barrier_report
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_barrier_report_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE UNIQUE INDEX uq_barrier_report_canonical_idempotency
    ON adherence.barrier_report (participant_id, idempotency_key);
CREATE INDEX ix_barrier_report_canonical_participant_session
    ON adherence.barrier_report (participant_id, planned_session_id, reported_at DESC);

ALTER TABLE adherence.recovery_episode ADD COLUMN participant_id UUID;
UPDATE adherence.recovery_episode episode
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE episode.participant_account_id = link.principal_account_id;
ALTER TABLE adherence.recovery_episode
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_recovery_episode_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE UNIQUE INDEX uq_recovery_episode_canonical_active
    ON adherence.recovery_episode (participant_id)
    WHERE status IN ('OPEN', 'RETURN_IN_PROGRESS');

ALTER TABLE safety.participant_restriction ADD COLUMN participant_id UUID;
UPDATE safety.participant_restriction restriction
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE restriction.account_id = link.principal_account_id;
ALTER TABLE safety.participant_restriction
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN account_id DROP NOT NULL,
    ADD CONSTRAINT fk_participant_restriction_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE UNIQUE INDEX uq_participant_restriction_canonical_tag
    ON safety.participant_restriction (participant_id, contraindication_tag);

ALTER TABLE safety.readiness_check_in ADD COLUMN participant_id UUID;
UPDATE safety.readiness_check_in check_in
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE check_in.account_id = link.principal_account_id;
ALTER TABLE safety.readiness_check_in
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN account_id DROP NOT NULL,
    ADD CONSTRAINT fk_readiness_check_in_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_readiness_canonical_participant_recorded
    ON safety.readiness_check_in (participant_id, recorded_at DESC);

ALTER TABLE safety.restriction ADD COLUMN participant_id UUID;
UPDATE safety.restriction restriction
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE restriction.participant_account_id = link.principal_account_id;
ALTER TABLE safety.restriction
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_safety_restriction_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_safety_restriction_canonical_participant
    ON safety.restriction (participant_id, status, valid_from, valid_to);

ALTER TABLE safety.plan_safety_assessment ADD COLUMN participant_id UUID;
UPDATE safety.plan_safety_assessment assessment
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE assessment.participant_account_id = link.principal_account_id;
ALTER TABLE safety.plan_safety_assessment
    ALTER COLUMN participant_id SET NOT NULL,
    ALTER COLUMN participant_account_id DROP NOT NULL,
    ADD CONSTRAINT fk_plan_safety_assessment_participant
        FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
CREATE INDEX ix_plan_safety_assessment_canonical_participant
    ON safety.plan_safety_assessment (participant_id, assessed_at DESC);

ALTER TABLE specialist.participant_specialist_relationship
    ALTER COLUMN participant_id SET NOT NULL,
    ADD CONSTRAINT uq_specialist_relationship_canonical_participant
        UNIQUE (specialist_account_id, participant_id);

COMMENT ON COLUMN calendar.appointment.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN training_planning.training_goal.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN training_planning.training_plan.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN training_planning.planned_session.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN training_execution.session_execution.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN training_execution.session_execution_attempt.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN training_execution.post_24h_response.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN training_execution.executed_load_aggregate.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN specialist.worklist_item.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN specialist.participant_issue.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN specialist.adherence_contact_signal.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN adherence.barrier_report.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN adherence.recovery_episode.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN safety.participant_restriction.account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN safety.readiness_check_in.account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN safety.restriction.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';
COMMENT ON COLUMN safety.plan_safety_assessment.participant_account_id IS 'Legacy account bridge; canonical identity is participant_id.';

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
