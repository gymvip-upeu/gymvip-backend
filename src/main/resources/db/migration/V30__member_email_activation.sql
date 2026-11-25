ALTER TABLE accounts ADD COLUMN temporary_password_expires_at TIMESTAMP;
CREATE UNIQUE INDEX uq_account_normalized_email ON accounts(lower(email));
CREATE TABLE member_access_invitations (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    revoked_at TIMESTAMP,
    email_status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    provider_id VARCHAR(250)
);
CREATE UNIQUE INDEX uq_member_active_invitation ON member_access_invitations(account_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
