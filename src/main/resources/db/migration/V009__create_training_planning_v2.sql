ALTER TABLE training_planning.training_plan
    ALTER COLUMN goal_id DROP NOT NULL,
    DROP CONSTRAINT ck_training_plan_mode,
    DROP CONSTRAINT ck_training_plan_status,
    ADD COLUMN purpose VARCHAR(500),
    ADD COLUMN owner_account_id UUID,
    ADD COLUMN current_revision_id UUID,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_training_plan_mode_v2 CHECK (
        plan_mode IN ('SPECIALIST_ASSIGNED', 'SELF_DIRECTED', 'SPECIALIST', 'COLLABORATIVE')
    ),
    ADD CONSTRAINT ck_training_plan_status_v2 CHECK (
        status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED')
    );
UPDATE training_planning.training_plan
SET owner_account_id = created_by_account_id,
    purpose = 'Legacy imported training plan';
ALTER TABLE training_planning.training_plan
    ALTER COLUMN owner_account_id SET NOT NULL,
    ALTER COLUMN purpose SET NOT NULL;

CREATE TABLE training_planning.plan_revision (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES training_planning.training_plan (id),
    revision_number INTEGER NOT NULL,
    based_on_revision_id UUID REFERENCES training_planning.plan_revision (id),
    status VARCHAR(32) NOT NULL,
    phase_intent VARCHAR(500) NOT NULL,
    valid_from DATE,
    valid_to DATE,
    author_account_id UUID NOT NULL,
    author_capability VARCHAR(80) NOT NULL,
    migration_origin VARCHAR(32) NOT NULL,
    assessment_status VARCHAR(32) NOT NULL,
    draft_updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_revision_number UNIQUE (plan_id, revision_number),
    CONSTRAINT ck_plan_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_plan_revision_status CHECK (
        status IN ('DRAFT', 'VALIDATING', 'READY', 'ACTIVE', 'SUPERSEDED', 'COMPLETED')
    ),
    CONSTRAINT ck_plan_revision_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_plan_revision_migration_origin CHECK (migration_origin IN ('NATIVE_V2', 'LEGACY_V1')),
    CONSTRAINT ck_plan_revision_assessment_status CHECK (
        assessment_status IN ('NOT_ASSESSED', 'PENDING', 'PASS', 'WARNING', 'HARD_BLOCK')
    )
);
CREATE INDEX ix_plan_revision_plan_status
    ON training_planning.plan_revision (plan_id, status, revision_number DESC);

INSERT INTO training_planning.plan_revision (
    id, plan_id, revision_number, status, phase_intent, author_account_id,
    author_capability, migration_origin, assessment_status, draft_updated_at, created_at, version
)
SELECT gen_random_uuid(), id, 1,
       CASE WHEN status = 'ACTIVE' THEN 'ACTIVE' ELSE 'COMPLETED' END,
       'Legacy imported plan', created_by_account_id, 'LEGACY_AUTHOR',
       'LEGACY_V1', 'NOT_ASSESSED', created_at, created_at, 0
FROM training_planning.training_plan;

UPDATE training_planning.training_plan plan
SET current_revision_id = revision.id
FROM training_planning.plan_revision revision
WHERE revision.plan_id = plan.id AND revision.revision_number = 1;
ALTER TABLE training_planning.training_plan
    ADD CONSTRAINT fk_training_plan_current_revision
        FOREIGN KEY (current_revision_id) REFERENCES training_planning.plan_revision (id);

ALTER TABLE training_planning.training_goal
    ADD COLUMN revision_id UUID REFERENCES training_planning.plan_revision (id),
    ADD COLUMN perspective VARCHAR(40),
    ADD COLUMN category VARCHAR(80),
    ADD COLUMN title VARCHAR(160),
    ADD COLUMN description VARCHAR(1000),
    ADD COLUMN priority INTEGER,
    ADD COLUMN status VARCHAR(24),
    ADD COLUMN target_date DATE,
    ADD CONSTRAINT ck_training_goal_perspective CHECK (
        perspective IS NULL OR perspective IN ('PERFORMANCE', 'FUNCTIONAL_RECOVERY', 'GENERAL_FITNESS')
    ),
    ADD CONSTRAINT ck_training_goal_priority CHECK (priority IS NULL OR priority BETWEEN 1 AND 100),
    ADD CONSTRAINT ck_training_goal_status CHECK (
        status IS NULL OR status IN ('ACTIVE', 'ACHIEVED', 'CANCELLED')
    );
UPDATE training_planning.training_goal goal
SET revision_id = revision.id,
    perspective = 'GENERAL_FITNESS',
    category = 'LEGACY',
    title = goal.name,
    priority = 50,
    status = 'ACTIVE'
FROM training_planning.training_plan plan
JOIN training_planning.plan_revision revision ON revision.plan_id = plan.id
WHERE plan.goal_id = goal.id AND revision.revision_number = 1;
CREATE INDEX ix_training_goal_revision
    ON training_planning.training_goal (revision_id, priority, id);

CREATE TABLE training_planning.goal_outcome (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES training_planning.training_goal (id) ON DELETE CASCADE,
    metric_code VARCHAR(80) NOT NULL,
    baseline NUMERIC(14, 4),
    target NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    measurement_method VARCHAR(500) NOT NULL,
    evidence_source VARCHAR(500),
    CONSTRAINT uq_goal_outcome_metric UNIQUE (goal_id, metric_code)
);

ALTER TABLE training_planning.training_cycle
    ADD COLUMN revision_id UUID REFERENCES training_planning.plan_revision (id),
    ADD COLUMN start_date DATE,
    ADD COLUMN end_date DATE,
    ADD COLUMN phase_intent VARCHAR(500),
    ADD COLUMN phase_goal VARCHAR(500),
    ADD CONSTRAINT ck_training_cycle_dates CHECK (
        end_date IS NULL OR start_date IS NULL OR end_date >= start_date
    );
UPDATE training_planning.training_cycle cycle
SET revision_id = revision.id,
    phase_intent = 'Legacy imported cycle'
FROM training_planning.plan_revision revision
WHERE revision.plan_id = cycle.plan_id AND revision.revision_number = 1;
CREATE UNIQUE INDEX uq_training_cycle_revision_sequence
    ON training_planning.training_cycle (revision_id, sequence_number)
    WHERE revision_id IS NOT NULL;

ALTER TABLE training_planning.microcycle
    ADD COLUMN start_date DATE,
    ADD COLUMN end_date DATE,
    ADD COLUMN phase_intent VARCHAR(500),
    ADD COLUMN phase_goal VARCHAR(500),
    ADD CONSTRAINT ck_microcycle_dates CHECK (
        end_date IS NULL OR start_date IS NULL OR end_date >= start_date
    );
UPDATE training_planning.microcycle
SET phase_intent = 'Legacy imported microcycle';

ALTER TABLE training_planning.planned_session
    DROP CONSTRAINT ck_planned_session_status,
    ADD COLUMN scheduled_date DATE,
    ADD COLUMN available_from TIMESTAMPTZ,
    ADD COLUMN available_to TIMESTAMPTZ,
    ADD COLUMN expected_duration_minutes INTEGER,
    ADD CONSTRAINT ck_planned_session_status_v2
        CHECK (status IN ('DRAFT', 'ASSIGNED', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_planned_session_availability
        CHECK (available_to IS NULL OR available_from IS NULL OR available_to >= available_from),
    ADD CONSTRAINT ck_planned_session_expected_duration
        CHECK (expected_duration_minutes IS NULL OR expected_duration_minutes > 0);

ALTER TABLE training_planning.exercise_prescription
    ADD COLUMN side VARCHAR(24),
    ADD COLUMN dose_type VARCHAR(40) NOT NULL DEFAULT 'LEGACY_UNTYPED',
    ADD COLUMN distance_meters NUMERIC(10, 2),
    ADD COLUMN target_contacts INTEGER,
    ADD COLUMN external_load_value NUMERIC(10, 2),
    ADD COLUMN external_load_unit VARCHAR(24),
    ADD COLUMN intensity_type VARCHAR(32),
    ADD COLUMN intensity_value NUMERIC(8, 2),
    ADD COLUMN intensity_zone VARCHAR(40),
    ADD COLUMN tempo VARCHAR(40),
    ADD COLUMN range_of_motion VARCHAR(40),
    ADD COLUMN rest_seconds INTEGER,
    ADD COLUMN substitute_group VARCHAR(80),
    ADD CONSTRAINT ck_prescription_side CHECK (
        side IS NULL OR side IN ('LEFT', 'RIGHT', 'BILATERAL', 'NOT_APPLICABLE')
    ),
    ADD CONSTRAINT ck_prescription_dose_type CHECK (
        dose_type IN (
            'LEGACY_UNTYPED', 'DYNAMIC_RESISTANCE', 'ISOMETRIC', 'IMPACT',
            'ENDURANCE', 'MOBILITY_CONTROL'
        )
    ),
    ADD CONSTRAINT ck_prescription_distance CHECK (distance_meters IS NULL OR distance_meters > 0),
    ADD CONSTRAINT ck_prescription_contacts CHECK (target_contacts IS NULL OR target_contacts > 0),
    ADD CONSTRAINT ck_prescription_external_load_pair CHECK (
        (external_load_value IS NULL AND external_load_unit IS NULL)
        OR (external_load_value IS NOT NULL AND external_load_value >= 0 AND external_load_unit IS NOT NULL)
    ),
    ADD CONSTRAINT ck_prescription_intensity_type CHECK (
        intensity_type IS NULL OR intensity_type IN ('RPE', 'RIR', 'PERCENT_1RM', 'ZONE')
    ),
    ADD CONSTRAINT ck_prescription_intensity_pair CHECK (
        (intensity_type IS NULL AND intensity_value IS NULL AND intensity_zone IS NULL)
        OR (intensity_type = 'ZONE' AND intensity_value IS NULL AND intensity_zone IS NOT NULL)
        OR (intensity_type IN ('RPE', 'RIR', 'PERCENT_1RM') AND intensity_value IS NOT NULL AND intensity_zone IS NULL)
    ),
    ADD CONSTRAINT ck_prescription_intensity_range CHECK (
        intensity_value IS NULL
        OR (intensity_type = 'RPE' AND intensity_value BETWEEN 0 AND 10)
        OR (intensity_type = 'RIR' AND intensity_value >= 0)
        OR (intensity_type = 'PERCENT_1RM' AND intensity_value BETWEEN 0 AND 100)
    ),
    ADD CONSTRAINT ck_prescription_typed_dose CHECK (
        dose_type = 'LEGACY_UNTYPED'
        OR (dose_type = 'DYNAMIC_RESISTANCE'
            AND target_sets IS NOT NULL AND target_repetitions IS NOT NULL
            AND target_duration_seconds IS NULL AND distance_meters IS NULL AND target_contacts IS NULL)
        OR (dose_type = 'ISOMETRIC'
            AND target_sets IS NOT NULL AND target_duration_seconds IS NOT NULL
            AND target_repetitions IS NULL AND distance_meters IS NULL AND target_contacts IS NULL)
        OR (dose_type = 'IMPACT'
            AND target_sets IS NOT NULL AND target_contacts IS NOT NULL
            AND target_repetitions IS NULL AND target_duration_seconds IS NULL AND distance_meters IS NULL)
        OR (dose_type = 'ENDURANCE'
            AND target_sets IS NULL AND target_repetitions IS NULL AND target_contacts IS NULL
            AND ((target_duration_seconds IS NOT NULL AND distance_meters IS NULL)
                 OR (target_duration_seconds IS NULL AND distance_meters IS NOT NULL)))
        OR (dose_type = 'MOBILITY_CONTROL'
            AND target_contacts IS NULL AND distance_meters IS NULL
            AND ((target_repetitions IS NOT NULL AND target_duration_seconds IS NULL)
                 OR (target_repetitions IS NULL AND target_duration_seconds IS NOT NULL)))
    ),
    ADD CONSTRAINT ck_prescription_rest CHECK (rest_seconds IS NULL OR rest_seconds >= 0);

CREATE TABLE training_planning.plan_load_budget (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES training_planning.plan_revision (id) ON DELETE CASCADE,
    channel VARCHAR(40) NOT NULL,
    budget_low NUMERIC(14, 4) NOT NULL,
    budget_high NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    action VARCHAR(16) NOT NULL,
    created_by_account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_plan_load_budget UNIQUE (revision_id, channel, unit),
    CONSTRAINT ck_plan_load_budget_range CHECK (
        budget_low >= 0 AND budget_low <= budget_high
    ),
    CONSTRAINT ck_plan_load_budget_action CHECK (action IN ('INFO', 'WARNING'))
);

CREATE TABLE training_planning.plan_revision_structural_validation (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES training_planning.plan_revision (id) ON DELETE CASCADE,
    draft_version BIGINT NOT NULL,
    input_checksum VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL,
    validated_at TIMESTAMPTZ NOT NULL,
    validated_by_account_id UUID NOT NULL,
    CONSTRAINT uq_plan_structural_validation_version UNIQUE (revision_id, draft_version),
    CONSTRAINT ck_plan_structural_validation_result CHECK (result IN ('PASS', 'FAIL'))
);
CREATE TABLE training_planning.plan_revision_structural_violation (
    validation_id UUID NOT NULL
        REFERENCES training_planning.plan_revision_structural_validation (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    code VARCHAR(80) NOT NULL,
    PRIMARY KEY (validation_id, position)
);
