ALTER TABLE exercise_set.exercise_set_version
    DROP CONSTRAINT ck_exercise_set_published_metadata;

ALTER TABLE exercise_set.exercise_set_analysis_run
    DROP CONSTRAINT exercise_set_analysis_run_status_check;

ALTER TABLE exercise_set.exercise_set_analysis_run
    ADD CONSTRAINT ck_exercise_set_analysis_run_status
    CHECK (status IN ('VALID', 'VALID_WITH_SUGGESTIONS', 'VALID_WITH_WARNINGS', 'BLOCKED',
                      'NO_SUGGESTIONS', 'SUGGESTIONS_AVAILABLE', 'ANALYSIS_UNAVAILABLE'));
