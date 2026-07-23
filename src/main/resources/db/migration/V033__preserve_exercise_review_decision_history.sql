-- A review is evidence of a decision at a point in time.  A later decision must not overwrite it.
ALTER TABLE exercise_catalog.exercise_review
    DROP CONSTRAINT uq_exercise_review_area;

ALTER TABLE exercise_catalog.exercise_version
    ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE exercise_catalog.exercise_review
    ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN invalidated_at TIMESTAMPTZ,
    ADD COLUMN invalidated_by_subject VARCHAR(255);

CREATE INDEX ix_exercise_review_latest
    ON exercise_catalog.exercise_review(exercise_version_id, review_area, content_revision, reviewed_at DESC, id DESC)
    WHERE invalidated_at IS NULL;
