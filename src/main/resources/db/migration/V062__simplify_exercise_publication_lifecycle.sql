CREATE OR REPLACE FUNCTION exercise_catalog.guard_exercise_publication() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'exercise version must be published through the editorial workflow';
    END IF;
    IF NEW.status = 'PUBLISHED' AND OLD.status <> 'PUBLISHED' THEN
        IF OLD.status NOT IN ('DRAFT', 'CHANGES_REQUESTED', 'IN_REVIEW', 'APPROVED') THEN
            RAISE EXCEPTION 'only an unpublished exercise version can be published';
        END IF;
        -- Import license validation produces a BLOCKER issue before a draft is created.
        IF EXISTS (
            SELECT 1 FROM exercise_import.import_issue issue
            JOIN exercise_import.import_record record ON record.id = issue.record_id
            WHERE record.draft_version_id = OLD.id AND issue.resolved_at IS NULL
              AND issue.severity IN ('ERROR', 'BLOCKER')
        ) THEN
            RAISE EXCEPTION 'unresolved import errors block publication';
        END IF;
    END IF;
    RETURN NEW;
END $$;

ALTER TABLE exercise_catalog.exercise_version
    DROP CONSTRAINT ck_exercise_v2_review_state,
    ADD CONSTRAINT ck_exercise_v2_review_state CHECK (
        profile_schema_version = 1
        OR status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED')
        OR (status IN ('PUBLISHED', 'WITHDRAWN') AND published_at IS NOT NULL)
    );

CREATE OR REPLACE FUNCTION exercise_catalog.guard_contribution_evidence_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF (TG_OP = 'INSERT' AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_contribution contribution JOIN exercise_catalog.exercise_version version ON version.id = contribution.exercise_version_id WHERE contribution.id = NEW.contribution_id AND version.status IN ('PUBLISHED', 'WITHDRAWN')))
       OR (TG_OP = 'DELETE' AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_contribution contribution JOIN exercise_catalog.exercise_version version ON version.id = contribution.exercise_version_id WHERE contribution.id = OLD.contribution_id AND version.status IN ('PUBLISHED', 'WITHDRAWN')))
       OR (TG_OP = 'UPDATE' AND EXISTS (SELECT 1 FROM exercise_catalog.exercise_contribution contribution JOIN exercise_catalog.exercise_version version ON version.id = contribution.exercise_version_id WHERE contribution.id IN (OLD.contribution_id, NEW.contribution_id) AND version.status IN ('PUBLISHED', 'WITHDRAWN'))) THEN
        RAISE EXCEPTION 'published exercise version is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_guard_contribution_evidence_mutation
BEFORE INSERT OR UPDATE OR DELETE ON exercise_catalog.exercise_contribution_evidence
FOR EACH ROW EXECUTE FUNCTION exercise_catalog.guard_contribution_evidence_mutation();

CREATE OR REPLACE FUNCTION exercise_catalog.guard_published_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status IN ('PUBLISHED', 'WITHDRAWN') THEN
            RAISE EXCEPTION 'published exercise version is immutable';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status IN ('PUBLISHED', 'WITHDRAWN') THEN
        IF NEW.status = 'WITHDRAWN' AND NEW.withdrawn_at IS NOT NULL
           AND OLD.status = 'PUBLISHED'
           AND NEW.exercise_id = OLD.exercise_id AND NEW.version_number = OLD.version_number
           AND NEW.instruction = OLD.instruction AND NEW.media_reference IS NOT DISTINCT FROM OLD.media_reference
           AND NEW.movement_pattern = OLD.movement_pattern AND NEW.stimulus_type = OLD.stimulus_type
           AND NEW.fatigue_profile = OLD.fatigue_profile AND NEW.technical_level = OLD.technical_level
           AND NEW.environment = OLD.environment AND NEW.locale = OLD.locale
           AND NEW.semantic_sha256 IS NOT DISTINCT FROM OLD.semantic_sha256 THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'published exercise version is immutable';
    END IF;
    RETURN NEW;
END $$;
