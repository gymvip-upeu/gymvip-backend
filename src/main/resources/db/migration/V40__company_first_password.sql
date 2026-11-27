ALTER TABLE accounts
    ADD COLUMN first_access_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN first_access_password_consumed_at TIMESTAMPTZ,
    ADD COLUMN first_access_original_hash VARCHAR(255);

ALTER TABLE company_activation_invitations
    ADD COLUMN delivery_mode VARCHAR(24) NOT NULL DEFAULT 'LINK';
