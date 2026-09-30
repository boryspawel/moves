ALTER TABLE exercise_catalog.exercise_version
    ALTER COLUMN movement_pattern DROP NOT NULL,
    ALTER COLUMN stimulus_type DROP NOT NULL,
    ALTER COLUMN fatigue_profile DROP NOT NULL,
    ALTER COLUMN technical_level DROP NOT NULL,
    ALTER COLUMN environment DROP NOT NULL;
