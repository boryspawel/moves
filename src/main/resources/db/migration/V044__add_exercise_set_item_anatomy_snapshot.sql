ALTER TABLE exercise_set.exercise_set_item
    ADD COLUMN anatomy_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb;
