-- P1 keeps historic planning rows readable while making new revision content traceable
-- to the canonical participant-goal and exact exercise-set-version sources.
ALTER TABLE training_planning.training_goal
    ADD COLUMN source_participant_goal_id UUID,
    ADD COLUMN source_participant_goal_version BIGINT,
    ADD COLUMN snapshotted_at TIMESTAMPTZ;

CREATE INDEX ix_training_goal_source_participant_goal
    ON training_planning.training_goal (source_participant_goal_id)
    WHERE source_participant_goal_id IS NOT NULL;

ALTER TABLE training_planning.planned_session
    ADD COLUMN source_exercise_set_id UUID,
    ADD COLUMN source_exercise_set_version_id UUID,
    ADD COLUMN source_snapshot JSONB;

CREATE INDEX ix_planned_session_source_set_version
    ON training_planning.planned_session (source_exercise_set_version_id)
    WHERE source_exercise_set_version_id IS NOT NULL;

ALTER TABLE training_planning.exercise_prescription
    ADD COLUMN source_exercise_set_item_id UUID,
    ADD COLUMN source_exercise_set_version_id UUID,
    ADD COLUMN canonical_dose_type VARCHAR(16),
    ADD COLUMN materialized_snapshot JSONB;

CREATE INDEX ix_exercise_prescription_source_set_version
    ON training_planning.exercise_prescription (source_exercise_set_version_id)
    WHERE source_exercise_set_version_id IS NOT NULL;

-- Legacy rows deliberately retain null source identifiers: their original source cannot
-- be inferred safely. Existing title/outcome/prescription columns remain their read-only
-- historical snapshots.
UPDATE training_planning.training_goal
SET snapshotted_at = created_at
WHERE snapshotted_at IS NULL AND revision_id IS NOT NULL;

ALTER TABLE load_analysis.planned_load_snapshot
    ADD COLUMN completeness_issues JSONB NOT NULL DEFAULT '[]'::jsonb;
