ALTER TABLE specialist.client_create_idempotency
    ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64);

-- Existing V036 rows cannot be reconstructed safely because their payload was not persisted.
-- The empty fingerprint intentionally rejects their replay rather than accepting a different payload.
UPDATE specialist.client_create_idempotency
SET request_fingerprint = ''
WHERE request_fingerprint IS NULL;

ALTER TABLE specialist.client_create_idempotency
    ALTER COLUMN request_fingerprint SET DEFAULT '',
    ALTER COLUMN request_fingerprint SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_specialist_relationship_specialist_active_participant
    ON specialist.participant_specialist_relationship (specialist_account_id, status, participant_id);
