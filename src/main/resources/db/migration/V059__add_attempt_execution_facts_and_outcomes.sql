-- P4 execution facts are append-only.  Legacy executions have no fact provenance;
-- their outcome is intentionally marked LEGACY rather than inferred as completed.
ALTER TABLE training_execution.session_execution
    ADD COLUMN outcome VARCHAR(24) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN stop_reason VARCHAR(80),
    ADD COLUMN attempt_id UUID;

ALTER TABLE training_execution.session_execution
    DROP CONSTRAINT ck_session_execution_declared,
    ADD CONSTRAINT ck_session_execution_declared
        CHECK ((outcome IN ('LEGACY', 'COMPLETED') AND declared_completion)
            OR (outcome IN ('PARTIAL', 'SKIPPED', 'STOPPED') AND NOT declared_completion));

CREATE UNIQUE INDEX uq_session_execution_attempt_id
    ON training_execution.session_execution (attempt_id) WHERE attempt_id IS NOT NULL;

ALTER TABLE training_execution.exercise_result
    ADD COLUMN actual_set_details JSONB;

CREATE TABLE training_execution.session_execution_attempt_fact (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES training_execution.session_execution_attempt (id),
    exercise_prescription_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    result_payload JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_attempt_execution_fact_revision UNIQUE (attempt_id, exercise_prescription_id, revision_number),
    CONSTRAINT ck_attempt_execution_fact_outcome CHECK (outcome IN ('PERFORMED', 'PARTIAL', 'SKIPPED'))
);
CREATE INDEX ix_attempt_execution_fact_current
    ON training_execution.session_execution_attempt_fact (attempt_id, exercise_prescription_id, revision_number DESC);

-- Adherence metric events are emitted by both legacy account-keyed flows and
-- canonical participant-keyed execution/adherence flows. Preserve historical
-- account values, and backfill canonical identities through the unique access link.
ALTER TABLE analytics.adherence_metric_event
    ADD COLUMN participant_id UUID;
UPDATE analytics.adherence_metric_event event
SET participant_id = link.participant_id
FROM participant.participant_access_link link
WHERE event.participant_account_id = link.principal_account_id
  AND event.participant_id IS NULL;
ALTER TABLE analytics.adherence_metric_event
    ADD CONSTRAINT fk_adherence_metric_event_participant
    FOREIGN KEY (participant_id) REFERENCES participant.participant_record (id);
ALTER TABLE analytics.adherence_metric_event
    ALTER COLUMN participant_account_id DROP NOT NULL;
ALTER TABLE analytics.adherence_metric_event
    ADD CONSTRAINT ck_adherence_metric_event_identity
    CHECK (participant_account_id IS NOT NULL OR participant_id IS NOT NULL);
COMMENT ON COLUMN analytics.adherence_metric_event.participant_account_id
    IS 'Legacy account identity for account-keyed metric producers.';
COMMENT ON COLUMN analytics.adherence_metric_event.participant_id
    IS 'Canonical participant identity for participant-keyed metric producers.';
