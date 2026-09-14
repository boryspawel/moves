CREATE TABLE participant.participant_access_invitation (
    id UUID PRIMARY KEY,
    participant_id UUID NOT NULL REFERENCES participant.participant_record(id),
    inviter_account_id UUID NOT NULL REFERENCES identity_access.principal_account(id),
    inviter_subject VARCHAR(255) NOT NULL,
    intended_email VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    claimed_account_id UUID REFERENCES identity_access.principal_account(id),
    claimed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_participant_access_invitation_status CHECK (status IN ('PENDING','CLAIMED','REVOKED'))
);
CREATE UNIQUE INDEX uq_participant_access_invitation_pending
    ON participant.participant_access_invitation(participant_id) WHERE status = 'PENDING';
CREATE INDEX ix_participant_access_invitation_participant ON participant.participant_access_invitation(participant_id);

CREATE TABLE participant.participant_claim_context (
    id UUID PRIMARY KEY,
    invitation_id UUID NOT NULL REFERENCES participant.participant_access_invitation(id) ON DELETE CASCADE,
    secret_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_participant_claim_context_invitation ON participant.participant_claim_context(invitation_id);
