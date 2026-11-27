ALTER TABLE accounts ADD COLUMN directory_only BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE member_signup_challenges (
    id UUID PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    ip_key VARCHAR(64) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    dni CHAR(8) NOT NULL,
    birth_date DATE NOT NULL,
    email_hash VARCHAR(255),
    whatsapp_hash VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    attempts SMALLINT NOT NULL DEFAULT 0,
    dispatched BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ
);
CREATE INDEX member_signup_challenge_email_time ON member_signup_challenges(email, created_at);
CREATE INDEX member_signup_challenge_phone_time ON member_signup_challenges(phone, created_at);
CREATE INDEX member_signup_challenge_ip_time ON member_signup_challenges(ip_key, created_at);
CREATE INDEX member_signup_challenge_created ON member_signup_challenges(created_at);

CREATE TABLE member_identities (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    declared_dni CHAR(8) NOT NULL,
    birth_date DATE NOT NULL,
    email_verified_at TIMESTAMPTZ NOT NULL,
    whatsapp_verified_at TIMESTAMPTZ NOT NULL,
    terms_version VARCHAR(30) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL
);
