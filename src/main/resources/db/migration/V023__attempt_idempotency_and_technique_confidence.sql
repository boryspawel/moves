ALTER TABLE training_execution.session_execution_attempt
    ADD COLUMN start_idempotency_key VARCHAR(120) NOT NULL DEFAULT $$legacy$$;

CREATE UNIQUE INDEX uq_session_execution_attempt_start_idempotency
    ON training_execution.session_execution_attempt (participant_account_id, start_idempotency_key)
    WHERE start_idempotency_key <> $$legacy$$;

ALTER TABLE training_execution.pain_difficulty_report
    ADD COLUMN technique_confidence_level INTEGER;

ALTER TABLE training_execution.pain_difficulty_report
    ADD CONSTRAINT ck_pain_difficulty_report_technique_confidence
    CHECK (technique_confidence_level IS NULL OR technique_confidence_level BETWEEN 1 AND 10);
