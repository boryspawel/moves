CREATE SCHEMA exercise_import;

CREATE TABLE exercise_import.import_source (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    default_locale VARCHAR(35) NOT NULL,
    license_code VARCHAR(120) NOT NULL,
    license_verified BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_import_source_code CHECK (code ~ '^[A-Z0-9_:-]+$')
);

CREATE TABLE exercise_import.import_batch (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES exercise_import.import_source(id),
    request_key VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL,
    forced_from_batch_id UUID REFERENCES exercise_import.import_batch(id),
    submitted_by_subject VARCHAR(255) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    total_count INTEGER NOT NULL DEFAULT 0,
    valid_count INTEGER NOT NULL DEFAULT 0,
    invalid_count INTEGER NOT NULL DEFAULT 0,
    blocked_count INTEGER NOT NULL DEFAULT 0,
    drafted_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_batch_request UNIQUE (source_id, request_key),
    CONSTRAINT ck_import_batch_status CHECK (status IN (
        'RECEIVED','QUEUED','PROCESSING','COMPLETED','COMPLETED_WITH_ISSUES','FAILED'
    )),
    CONSTRAINT ck_import_batch_counts CHECK (
        total_count >= 0 AND valid_count >= 0 AND invalid_count >= 0 AND blocked_count >= 0
        AND drafted_count >= 0 AND unchanged_count >= 0
    )
);
CREATE INDEX ix_import_batch_source_submitted
    ON exercise_import.import_batch(source_id, submitted_at DESC, id);
CREATE INDEX ix_import_batch_status ON exercise_import.import_batch(status, submitted_at, id);

CREATE TABLE exercise_import.import_artifact (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL UNIQUE REFERENCES exercise_import.import_batch(id),
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(120) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_import_artifact_size CHECK (byte_size > 0),
    CONSTRAINT ck_import_artifact_sha CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_import_artifact_sha ON exercise_import.import_artifact(sha256);
CREATE TABLE exercise_import.import_record (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES exercise_import.import_batch(id) ON DELETE CASCADE,
    row_number BIGINT NOT NULL,
    source_record_key VARCHAR(240),
    status VARCHAR(40) NOT NULL,
    raw_payload JSONB NOT NULL,
    raw_sha256 CHAR(64) NOT NULL,
    normalized_payload JSONB,
    normalized_sha256 CHAR(64),
    normalization_version VARCHAR(40),
    matched_exercise_id UUID REFERENCES exercise_catalog.exercise(id),
    draft_version_id UUID UNIQUE REFERENCES exercise_catalog.exercise_version(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    processing_token UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_record_row UNIQUE(batch_id, row_number),
    CONSTRAINT uq_import_record_source_key UNIQUE(batch_id, source_record_key),
    CONSTRAINT ck_import_record_row CHECK (row_number > 0),
    CONSTRAINT ck_import_record_status CHECK (status IN (
        'RECEIVED','PARSED','NORMALIZED','INVALID','BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE',
        'MATCH_CANDIDATES','READY_FOR_DRAFT','DRAFTED','UNCHANGED','REJECTED'
    )),
    CONSTRAINT ck_import_record_raw_sha CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_import_record_normalized_sha CHECK (
        normalized_sha256 IS NULL OR normalized_sha256 ~ '^[0-9a-f]{64}$'
    )
);
CREATE INDEX ix_import_record_batch_status
    ON exercise_import.import_record(batch_id, status, row_number, id);
CREATE INDEX ix_import_record_source_key
    ON exercise_import.import_record(source_record_key) WHERE source_record_key IS NOT NULL;
CREATE INDEX ix_import_record_raw_hash ON exercise_import.import_record(raw_sha256);
CREATE INDEX ix_import_record_normalized_hash
    ON exercise_import.import_record(normalized_sha256) WHERE normalized_sha256 IS NOT NULL;

CREATE TABLE exercise_import.import_source_reference (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES exercise_import.import_source(id),
    source_record_key VARCHAR(240) NOT NULL,
    exercise_id UUID NOT NULL REFERENCES exercise_catalog.exercise(id),
    latest_exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id),
    normalized_sha256 CHAR(64) NOT NULL,
    first_record_id UUID NOT NULL REFERENCES exercise_import.import_record(id),
    last_record_id UUID NOT NULL REFERENCES exercise_import.import_record(id),
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_source_reference UNIQUE(source_id, source_record_key),
    CONSTRAINT ck_import_source_reference_sha CHECK (normalized_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_import_source_reference_exercise
    ON exercise_import.import_source_reference(exercise_id);
CREATE INDEX ix_import_source_reference_hash
    ON exercise_import.import_source_reference(source_id, normalized_sha256);

CREATE TABLE exercise_import.import_mapping (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES exercise_import.import_source(id),
    dictionary_type VARCHAR(50) NOT NULL,
    source_value VARCHAR(240) NOT NULL,
    canonical_value VARCHAR(240),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    decided_by_subject VARCHAR(255),
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_mapping UNIQUE(source_id, dictionary_type, source_value),
    CONSTRAINT ck_import_mapping_type CHECK (dictionary_type IN (
        'EQUIPMENT','POSITION','DOSE_UNIT','MOVEMENT_PATTERN','ANATOMY'
    )),
    CONSTRAINT ck_import_mapping_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_import_mapping_decision CHECK (
        (status = 'PENDING' AND decided_by_subject IS NULL AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_by_subject IS NOT NULL AND decided_at IS NOT NULL)
    )
);
CREATE INDEX ix_import_mapping_pending ON exercise_import.import_mapping(source_id, status, dictionary_type);

CREATE TABLE exercise_import.import_issue (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES exercise_import.import_batch(id) ON DELETE CASCADE,
    record_id UUID REFERENCES exercise_import.import_record(id) ON DELETE CASCADE,
    row_number BIGINT,
    code VARCHAR(100) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    json_pointer VARCHAR(500) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_subject VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_import_issue_stage CHECK (stage IN (
        'RECEIVE','PARSE','NORMALIZE','VALIDATE','MATCH','CREATE_DRAFT','REVIEW','PUBLISH'
    )),
    CONSTRAINT ck_import_issue_severity CHECK (severity IN ('INFO','WARNING','ERROR','BLOCKER'))
);
CREATE UNIQUE INDEX uq_import_issue_stable
    ON exercise_import.import_issue(batch_id, COALESCE(record_id, '00000000-0000-0000-0000-000000000000'::uuid), code, json_pointer);
CREATE INDEX ix_import_issue_batch_severity
    ON exercise_import.import_issue(batch_id, severity, row_number, id);
CREATE INDEX ix_import_issue_record ON exercise_import.import_issue(record_id, severity, id);

CREATE TABLE exercise_import.import_match_candidate (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES exercise_import.import_record(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercise_catalog.exercise(id),
    rank INTEGER NOT NULL,
    score NUMERIC(8,5) NOT NULL,
    reasons JSONB NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    decision VARCHAR(16),
    decided_by_subject VARCHAR(255),
    decided_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_match_exercise UNIQUE(record_id, exercise_id),
    CONSTRAINT uq_import_match_rank UNIQUE(record_id, rank),
    CONSTRAINT ck_import_match_rank CHECK (rank BETWEEN 1 AND 10),
    CONSTRAINT ck_import_match_score CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT ck_import_match_decision CHECK (decision IS NULL OR decision IN ('SAME','DIFFERENT','UNSURE')),
    CONSTRAINT ck_import_match_decision_audit CHECK (
        (decision IS NULL AND decided_by_subject IS NULL AND decided_at IS NULL)
        OR (decision IS NOT NULL AND decided_by_subject IS NOT NULL AND decided_at IS NOT NULL)
    )
);
CREATE INDEX ix_import_match_record_rank ON exercise_import.import_match_candidate(record_id, rank, id);

CREATE TABLE exercise_catalog.exercise_equipment_dictionary (
    code VARCHAR(80) PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    dictionary_version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE exercise_catalog.exercise_position_dictionary (
    code VARCHAR(80) PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    dictionary_version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE exercise_catalog.dose_unit_dictionary (
    code VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    dictionary_version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO exercise_catalog.exercise_equipment_dictionary(code, display_name) VALUES
    ('BODYWEIGHT','Masa ciała'), ('MAT','Mata'), ('DUMBBELL','Hantel'), ('BAND','Guma oporowa');
INSERT INTO exercise_catalog.exercise_position_dictionary(code, display_name) VALUES
    ('STANDING','Stanie'), ('SUPINE','Leżenie tyłem'), ('PRONE','Leżenie przodem'), ('KNEELING','Klęk');
INSERT INTO exercise_catalog.dose_unit_dictionary(code, display_name) VALUES
    ('REP','Powtórzenie'), ('SECOND','Sekunda'), ('METER','Metr');

ALTER TABLE exercise_catalog.exercise_version
    ADD COLUMN locale VARCHAR(35) NOT NULL DEFAULT 'pl-PL',
    ADD COLUMN semantic_sha256 CHAR(64),
    ADD COLUMN import_record_id UUID UNIQUE REFERENCES exercise_import.import_record(id),
    ADD CONSTRAINT ck_exercise_version_semantic_sha CHECK (
        semantic_sha256 IS NULL OR semantic_sha256 ~ '^[0-9a-f]{64}$'
    );
CREATE INDEX ix_exercise_version_exercise ON exercise_catalog.exercise_version(exercise_id, version_number DESC);
CREATE INDEX ix_exercise_version_semantic_sha
    ON exercise_catalog.exercise_version(semantic_sha256) WHERE semantic_sha256 IS NOT NULL;

CREATE TABLE exercise_catalog.exercise_version_purpose (
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    purpose VARCHAR(40) NOT NULL,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    PRIMARY KEY(exercise_version_id, purpose),
    CONSTRAINT ck_exercise_version_purpose CHECK (purpose IN (
        'TRAINING','THERAPEUTIC_EXERCISE','ASSESSMENT','WARM_UP','RECOVERY'
    ))
);
CREATE TABLE exercise_catalog.exercise_version_text (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    locale VARCHAR(35) NOT NULL,
    name VARCHAR(160) NOT NULL,
    summary TEXT,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    CONSTRAINT uq_exercise_version_text UNIQUE(exercise_version_id, locale)
);
CREATE TABLE exercise_catalog.exercise_instruction_step (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    locale VARCHAR(35) NOT NULL,
    step_number INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    CONSTRAINT uq_exercise_instruction_step UNIQUE(exercise_version_id, locale, step_number),
    CONSTRAINT ck_exercise_instruction_step_number CHECK(step_number > 0)
);
CREATE TABLE exercise_catalog.exercise_alias (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES exercise_catalog.exercise(id) ON DELETE CASCADE,
    locale VARCHAR(35) NOT NULL,
    alias VARCHAR(160) NOT NULL,
    normalized_alias VARCHAR(160) NOT NULL,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    CONSTRAINT uq_exercise_alias UNIQUE(exercise_id, locale, normalized_alias)
);
CREATE INDEX ix_exercise_alias_lookup ON exercise_catalog.exercise_alias(locale, normalized_alias, exercise_id);
CREATE TABLE exercise_catalog.exercise_relation (
    id UUID PRIMARY KEY,
    source_exercise_id UUID NOT NULL REFERENCES exercise_catalog.exercise(id),
    target_exercise_id UUID NOT NULL REFERENCES exercise_catalog.exercise(id),
    relation_type VARCHAR(40) NOT NULL,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    CONSTRAINT uq_exercise_relation UNIQUE(source_exercise_id, target_exercise_id, relation_type),
    CONSTRAINT ck_exercise_relation_distinct CHECK(source_exercise_id <> target_exercise_id),
    CONSTRAINT ck_exercise_relation_type CHECK(relation_type IN ('VARIANT_OF','PROGRESSION_OF','REGRESSION_OF','RELATED_TO'))
);
CREATE TABLE exercise_catalog.exercise_movement_characteristic (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    movement_pattern VARCHAR(64) NOT NULL,
    position_code VARCHAR(80) NOT NULL REFERENCES exercise_catalog.exercise_position_dictionary(code),
    unilateral BOOLEAN NOT NULL,
    load_nature VARCHAR(40) NOT NULL,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    CONSTRAINT uq_exercise_movement_characteristic UNIQUE(exercise_version_id, movement_pattern, position_code, unilateral, load_nature),
    CONSTRAINT ck_exercise_movement_pattern CHECK(movement_pattern IN ('SQUAT','HINGE','PUSH','PULL','LUNGE','CARRY','ROTATION','LOCOMOTION','BREATHING','MOBILITY','OTHER')),
    CONSTRAINT ck_exercise_load_nature CHECK(load_nature IN ('BODYWEIGHT','EXTERNAL','ASSISTED','MIXED'))
);
CREATE TABLE exercise_catalog.exercise_equipment (
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    equipment_code VARCHAR(80) NOT NULL REFERENCES exercise_catalog.exercise_equipment_dictionary(code),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    PRIMARY KEY(exercise_version_id, equipment_code)
);
CREATE TABLE exercise_catalog.exercise_dose_capability (
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    unit_code VARCHAR(40) NOT NULL REFERENCES exercise_catalog.dose_unit_dictionary(code),
    minimum_value NUMERIC(12,3),
    maximum_value NUMERIC(12,3),
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    PRIMARY KEY(exercise_version_id, unit_code),
    CONSTRAINT ck_exercise_dose_range CHECK(minimum_value IS NULL OR maximum_value IS NULL OR minimum_value <= maximum_value)
);

ALTER TABLE exercise_catalog.evidence_source
    ADD COLUMN source_type VARCHAR(40) NOT NULL DEFAULT 'EDITORIAL',
    ADD COLUMN license_code VARCHAR(120),
    ADD COLUMN provenance_source_id UUID REFERENCES exercise_import.import_source(id);
CREATE TABLE exercise_catalog.exercise_evidence_link (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    evidence_source_id UUID NOT NULL REFERENCES exercise_catalog.evidence_source(id) ON DELETE CASCADE,
    claim_type VARCHAR(60) NOT NULL,
    json_pointer VARCHAR(500) NOT NULL,
    CONSTRAINT uq_exercise_evidence_link UNIQUE(exercise_version_id, evidence_source_id, claim_type, json_pointer)
);
CREATE TABLE exercise_catalog.media_asset (
    id UUID PRIMARY KEY,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    media_type VARCHAR(120) NOT NULL,
    sha256 CHAR(64) NOT NULL UNIQUE,
    license_code VARCHAR(120) NOT NULL,
    provenance_source_id UUID REFERENCES exercise_import.import_source(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_media_asset_sha CHECK(sha256 ~ '^[0-9a-f]{64}$')
);
CREATE TABLE exercise_catalog.exercise_media (
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    media_asset_id UUID NOT NULL REFERENCES exercise_catalog.media_asset(id),
    role VARCHAR(40) NOT NULL,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY(exercise_version_id, media_asset_id, role),
    CONSTRAINT uq_exercise_media_order UNIQUE(exercise_version_id, role, sort_order),
    CONSTRAINT ck_exercise_media_order CHECK(sort_order >= 0)
);
CREATE TABLE exercise_catalog.exercise_review (
    id UUID PRIMARY KEY,
    exercise_version_id UUID NOT NULL REFERENCES exercise_catalog.exercise_version(id) ON DELETE CASCADE,
    review_area VARCHAR(32) NOT NULL,
    decision VARCHAR(24) NOT NULL,
    comment TEXT,
    reviewer_subject VARCHAR(255) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_exercise_review_area UNIQUE(exercise_version_id, review_area, reviewer_subject),
    CONSTRAINT ck_exercise_review_area CHECK(review_area IN ('CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE','MEDIA')),
    CONSTRAINT ck_exercise_review_decision CHECK(decision IN ('APPROVED','CHANGES_REQUESTED'))
);
CREATE INDEX ix_exercise_review_version ON exercise_catalog.exercise_review(exercise_version_id, review_area, decision);

CREATE OR REPLACE FUNCTION exercise_catalog.guard_exercise_publication() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE required_reviews INTEGER;
DECLARE approved_reviews INTEGER;
DECLARE distinct_reviewers INTEGER;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'exercise version must be published through the editorial workflow';
    END IF;
    IF NEW.status = 'PUBLISHED' AND OLD.status <> 'PUBLISHED' THEN
        IF OLD.status <> 'APPROVED' THEN
            RAISE EXCEPTION 'exercise version must be approved before publication';
        END IF;
        required_reviews := 4 + CASE WHEN EXISTS (
            SELECT 1 FROM exercise_catalog.exercise_media media WHERE media.exercise_version_id = OLD.id
        ) THEN 1 ELSE 0 END;
        SELECT COUNT(*), COUNT(DISTINCT reviewer_subject)
          INTO approved_reviews, distinct_reviewers
          FROM (
              SELECT DISTINCT ON (review_area) review_area, decision, reviewer_subject
                FROM exercise_catalog.exercise_review
               WHERE exercise_version_id = OLD.id
               ORDER BY review_area, reviewed_at DESC, id DESC
          ) review
         WHERE review.decision = 'APPROVED'
           AND review.review_area IN ('CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE','MEDIA');
        IF approved_reviews < required_reviews THEN
            RAISE EXCEPTION 'required independent review areas are not approved';
        END IF;
        IF EXISTS (
            SELECT 1 FROM (VALUES ('CONTENT'),('TECHNIQUE'),('ANATOMY_EXPOSURE'),('LICENSE')) required(area)
            WHERE NOT EXISTS (
                SELECT 1 FROM exercise_catalog.exercise_review review
                 WHERE review.exercise_version_id=OLD.id AND review.review_area=required.area
                   AND review.decision='APPROVED'
                   AND NOT EXISTS (
                       SELECT 1 FROM exercise_catalog.exercise_review newer
                        WHERE newer.exercise_version_id=review.exercise_version_id
                          AND newer.review_area=review.review_area
                          AND (newer.reviewed_at,newer.id)>(review.reviewed_at,review.id)
                   )
            )
        ) THEN RAISE EXCEPTION 'content, technique, anatomy/exposure and license approvals are required';
        END IF;
        IF EXISTS (SELECT 1 FROM exercise_catalog.exercise_media media WHERE media.exercise_version_id=OLD.id)
           AND NOT EXISTS (
               SELECT 1 FROM exercise_catalog.exercise_review review
                WHERE review.exercise_version_id=OLD.id AND review.review_area='MEDIA' AND review.decision='APPROVED'
                  AND NOT EXISTS (
                      SELECT 1 FROM exercise_catalog.exercise_review newer
                       WHERE newer.exercise_version_id=review.exercise_version_id
                         AND newer.review_area=review.review_area
                         AND (newer.reviewed_at,newer.id)>(review.reviewed_at,review.id)
                  )
           ) THEN RAISE EXCEPTION 'media approval is required';
        END IF;
        IF EXISTS (
            SELECT 1 FROM exercise_catalog.exercise_version_purpose purpose
             WHERE purpose.exercise_version_id = OLD.id AND purpose.purpose = 'THERAPEUTIC_EXERCISE'
        ) AND distinct_reviewers < 2 THEN
            RAISE EXCEPTION 'therapeutic exercise requires at least two independent reviewers';
        END IF;
        IF EXISTS (
            SELECT 1 FROM exercise_import.import_issue issue
            JOIN exercise_import.import_record record ON record.id = issue.record_id
            WHERE record.draft_version_id = OLD.id AND issue.resolved_at IS NULL
              AND issue.severity IN ('ERROR','BLOCKER')
        ) THEN
            RAISE EXCEPTION 'unresolved import errors block publication';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_exercise_version_publication_guard
BEFORE INSERT OR UPDATE ON exercise_catalog.exercise_version
FOR EACH ROW EXECUTE FUNCTION exercise_catalog.guard_exercise_publication();

CREATE OR REPLACE FUNCTION exercise_import.reject_raw_record_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.raw_payload IS DISTINCT FROM OLD.raw_payload OR NEW.raw_sha256 IS DISTINCT FROM OLD.raw_sha256
       OR NEW.batch_id IS DISTINCT FROM OLD.batch_id OR NEW.row_number IS DISTINCT FROM OLD.row_number THEN
        RAISE EXCEPTION 'raw import record is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_import_record_raw_immutable BEFORE UPDATE ON exercise_import.import_record
FOR EACH ROW EXECUTE FUNCTION exercise_import.reject_raw_record_mutation();

CREATE OR REPLACE FUNCTION exercise_catalog.guard_published_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status IN ('PUBLISHED','WITHDRAWN') THEN
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
CREATE TRIGGER trg_exercise_version_immutable BEFORE UPDATE OR DELETE ON exercise_catalog.exercise_version
FOR EACH ROW EXECUTE FUNCTION exercise_catalog.guard_published_version();

CREATE OR REPLACE FUNCTION exercise_catalog.guard_published_child() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE version_id UUID;
BEGIN
    version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.exercise_version_id ELSE NEW.exercise_version_id END;
    IF EXISTS (SELECT 1 FROM exercise_catalog.exercise_version WHERE id = version_id AND status IN ('PUBLISHED','WITHDRAWN')) THEN
        RAISE EXCEPTION 'semantic children of a published exercise version are immutable';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$;
DO $$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'exercise_version_movement_pattern','exercise_version_equipment','exercise_version_contraindication',
        'exercise_load_characteristic','evidence_source','exercise_contribution','exercise_version_purpose',
        'exercise_version_text','exercise_instruction_step','exercise_movement_characteristic',
        'exercise_equipment','exercise_dose_capability','exercise_evidence_link','exercise_media'
    ] LOOP
        EXECUTE format('CREATE TRIGGER trg_%s_immutable BEFORE INSERT OR UPDATE OR DELETE ON exercise_catalog.%I FOR EACH ROW EXECUTE FUNCTION exercise_catalog.guard_published_child()', table_name, table_name);
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION exercise_import.refresh_batch_projection(target_batch UUID) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE record_total INTEGER;
BEGIN
    SELECT COUNT(*) INTO record_total FROM exercise_import.import_record WHERE batch_id = target_batch;
    UPDATE exercise_import.import_batch batch SET
        total_count = record_total,
        valid_count = (SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status NOT IN ('INVALID','BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','REJECTED')),
        invalid_count = (SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status='INVALID'),
        blocked_count = (SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status IN ('BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','MATCH_CANDIDATES')),
        drafted_count = (SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status='DRAFTED'),
        unchanged_count = (SELECT COUNT(*) FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status='UNCHANGED'),
        status = CASE
            WHEN batch.status = 'FAILED' THEN 'FAILED'
            WHEN record_total = 0 AND batch.status IN ('RECEIVED','QUEUED') THEN batch.status
            WHEN EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status IN ('RECEIVED','PARSED','NORMALIZED')) THEN 'PROCESSING'
            WHEN EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status IN ('INVALID','BLOCKED_BY_MAPPING','BLOCKED_BY_LICENSE','MATCH_CANDIDATES')) THEN 'COMPLETED_WITH_ISSUES'
            ELSE 'COMPLETED'
        END,
        completed_at = CASE
            WHEN record_total > 0 AND NOT EXISTS (SELECT 1 FROM exercise_import.import_record r WHERE r.batch_id=target_batch AND r.status IN ('RECEIVED','PARSED','NORMALIZED'))
            THEN COALESCE(batch.completed_at, now()) ELSE NULL END,
        version = batch.version + 1
    WHERE batch.id = target_batch;
END $$;
