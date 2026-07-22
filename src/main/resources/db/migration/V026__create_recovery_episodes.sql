CREATE TABLE adherence.recovery_policy_version (
 code VARCHAR(80) PRIMARY KEY, version INTEGER NOT NULL, status VARCHAR(16) NOT NULL, no_start_days INTEGER NOT NULL, missed_window_count INTEGER NOT NULL, regularity_days INTEGER NOT NULL, regularity_min_starts INTEGER NOT NULL, short_gap_max_days INTEGER NOT NULL, medium_gap_max_days INTEGER NOT NULL, published_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT ck_recovery_policy_status CHECK (status IN ('ACTIVE','RETIRED'))
);
INSERT INTO adherence.recovery_policy_version VALUES ('RECOVERY_RETURN_V1',1,'ACTIVE',3,2,28,3,6,13,now());
CREATE TABLE adherence.recovery_episode (
 id UUID PRIMARY KEY, participant_account_id UUID NOT NULL, status VARCHAR(24) NOT NULL, policy_version_code VARCHAR(80) NOT NULL REFERENCES adherence.recovery_policy_version(code), opened_at TIMESTAMPTZ NOT NULL, participant_time_zone VARCHAR(80) NOT NULL, detected_local_date DATE NOT NULL, plan_revision_id_at_opening UUID NOT NULL, primary_trigger VARCHAR(48) NOT NULL, known_reason VARCHAR(32), source_barrier_report_id UUID REFERENCES adherence.barrier_report(id), gap_days INTEGER NOT NULL, missed_window_count INTEGER NOT NULL, last_session_started_at TIMESTAMPTZ, selected_path VARCHAR(40), selected_at TIMESTAMPTZ, target_planned_session_id UUID, return_attempt_id UUID, return_started_at TIMESTAMPTZ, return_local_date DATE, first_session_execution_id UUID, first_session_outcome VARCHAR(16), resolved_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_recovery_episode_status CHECK (status IN ('OPEN','RETURN_IN_PROGRESS','RESOLVED','CLOSED')),
 CONSTRAINT ck_recovery_episode_outcome CHECK (first_session_outcome IS NULL OR first_session_outcome IN ('COMPLETED','ABANDONED'))
);
CREATE UNIQUE INDEX uq_recovery_episode_active ON adherence.recovery_episode(participant_account_id) WHERE status IN ('OPEN','RETURN_IN_PROGRESS');
CREATE TABLE adherence.recovery_episode_evidence (id UUID PRIMARY KEY, recovery_episode_id UUID NOT NULL REFERENCES adherence.recovery_episode(id), evidence_type VARCHAR(48) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, details_code VARCHAR(80));
CREATE TABLE adherence.recovery_offer (id UUID PRIMARY KEY, recovery_episode_id UUID NOT NULL REFERENCES adherence.recovery_episode(id), plan_revision_id UUID NOT NULL, safety_state VARCHAR(32) NOT NULL, policy_version_code VARCHAR(80) NOT NULL, created_at TIMESTAMPTZ NOT NULL, stale_at TIMESTAMPTZ);
CREATE TABLE adherence.recovery_offer_option (id UUID PRIMARY KEY, recovery_offer_id UUID NOT NULL REFERENCES adherence.recovery_offer(id), ordinal INTEGER NOT NULL, path VARCHAR(40) NOT NULL, primary_option BOOLEAN NOT NULL, UNIQUE(recovery_offer_id, ordinal));
CREATE TABLE adherence.recovery_choice (id UUID PRIMARY KEY, recovery_episode_id UUID NOT NULL REFERENCES adherence.recovery_episode(id), recovery_offer_id UUID NOT NULL REFERENCES adherence.recovery_offer(id), path VARCHAR(40) NOT NULL, idempotency_key VARCHAR(120) NOT NULL, chosen_at TIMESTAMPTZ NOT NULL, UNIQUE(recovery_episode_id,idempotency_key));
ALTER TABLE specialist.adherence_contact_signal ALTER COLUMN barrier_report_id DROP NOT NULL;
ALTER TABLE specialist.adherence_contact_signal ADD COLUMN recovery_episode_id UUID UNIQUE REFERENCES adherence.recovery_episode(id);
ALTER TABLE specialist.adherence_contact_signal ADD CONSTRAINT ck_adherence_contact_signal_source CHECK ((barrier_report_id IS NULL) <> (recovery_episode_id IS NULL));
