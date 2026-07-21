ALTER TABLE training_planning.planned_session
    ADD COLUMN creation_source VARCHAR(32);

UPDATE training_planning.planned_session
SET creation_source = 'LEGACY_V1';

ALTER TABLE training_planning.planned_session
    ALTER COLUMN creation_source SET DEFAULT 'TRAINING_PLANNING',
    ALTER COLUMN creation_source SET NOT NULL,
    ADD CONSTRAINT ck_planned_session_creation_source
        CHECK (creation_source IN ('LEGACY_V1', 'TRAINING_PLANNING')),
    ADD CONSTRAINT ck_offline_appointment_legacy_only
        CHECK (session_kind <> 'OFFLINE_APPOINTMENT' OR creation_source = 'LEGACY_V1');

CREATE UNIQUE INDEX uq_session_execution_successful_session
    ON training_execution.session_execution (planned_session_id)
    WHERE declared_completion;
