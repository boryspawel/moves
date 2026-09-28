ALTER TABLE training_execution.session_execution
    ADD COLUMN recorder_account_id UUID,
    ADD COLUMN recording_source VARCHAR(32);

UPDATE training_execution.session_execution
SET recording_source = 'LEGACY_UNKNOWN'
WHERE recording_source IS NULL;

ALTER TABLE training_execution.session_execution
    ALTER COLUMN recording_source SET NOT NULL;
