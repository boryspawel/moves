ALTER TABLE exercise_set.exercise_set_version
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE exercise_set.exercise_set_version
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE exercise_set.exercise_set_version
    ALTER COLUMN updated_at SET NOT NULL;
