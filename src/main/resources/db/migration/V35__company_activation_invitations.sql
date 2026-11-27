CREATE TABLE company_activation_invitations (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE REFERENCES organization_requests(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    sent_by VARCHAR(150) NOT NULL,
    email_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_id VARCHAR(255)
);

UPDATE organization_requests SET activation_token = NULL WHERE activation_token IS NOT NULL;
