CREATE TABLE exercise_set.exercise_set_analysis_run (
    id UUID PRIMARY KEY,
    exercise_set_version_id UUID NOT NULL UNIQUE REFERENCES exercise_set.exercise_set_version(id) ON DELETE CASCADE,
    policy_version VARCHAR(32) NOT NULL,
    analyzed_lock_version BIGINT NOT NULL CHECK (analyzed_lock_version >= 0),
    analyzed_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('VALID','VALID_WITH_SUGGESTIONS','VALID_WITH_WARNINGS','BLOCKED')),
    item_count INTEGER NOT NULL CHECK (item_count >= 0),
    estimated_seconds INTEGER,
    time_confidence VARCHAR(16) NOT NULL CHECK (time_confidence IN ('COMPLETE','PARTIAL','UNAVAILABLE')),
    equipment_transitions INTEGER NOT NULL CHECK (equipment_transitions >= 0),
    dose_kind_switches INTEGER NOT NULL CHECK (dose_kind_switches >= 0),
    anatomy_data_available BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE exercise_set.exercise_set_analysis_finding (
    id UUID PRIMARY KEY,
    analysis_run_id UUID NOT NULL REFERENCES exercise_set.exercise_set_analysis_run(id) ON DELETE CASCADE,
    finding_ordinal INTEGER NOT NULL CHECK (finding_ordinal > 0),
    code VARCHAR(80) NOT NULL, rule_version VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('SUGGESTION','WARNING','BLOCKING')),
    category VARCHAR(24) NOT NULL CHECK (category IN ('STRUCTURE','TIME','EQUIPMENT','DUPLICATE','DATA_LIMITATION')),
    message_key VARCHAR(120) NOT NULL, explanation TEXT, item_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    phase VARCHAR(16), field VARCHAR(80), action VARCHAR(120), blocking BOOLEAN NOT NULL
);
CREATE UNIQUE INDEX uq_exercise_set_analysis_finding_ordinal ON exercise_set.exercise_set_analysis_finding(analysis_run_id, finding_ordinal);
CREATE INDEX ix_exercise_set_analysis_finding_run_code ON exercise_set.exercise_set_analysis_finding(analysis_run_id, code);
