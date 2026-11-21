CREATE TABLE membership_payment_links (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    plan_id UUID NOT NULL,
    operation VARCHAR(12) NOT NULL,
    amount_pen NUMERIC(12,2) NOT NULL,
    provider_link_id VARCHAR(80) UNIQUE,
    provider_url VARCHAR(600),
    provider_transaction_id VARCHAR(100) UNIQUE,
    created_by_email VARCHAR(254) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CREATING',
    sale_payment_id UUID,
    created_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_membership_payment_links_client ON membership_payment_links(client_id, created_at_utc DESC);
