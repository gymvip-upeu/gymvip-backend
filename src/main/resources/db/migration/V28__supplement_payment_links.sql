CREATE TABLE supplement_payment_links (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES supplement_orders(id),
    amount_pen NUMERIC(12,2) NOT NULL CHECK (amount_pen > 0),
    provider_link_id VARCHAR(80) NOT NULL UNIQUE,
    provider_url VARCHAR(600) NOT NULL,
    provider_transaction_id VARCHAR(100) UNIQUE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'EXPIRED', 'REVIEW', 'PAID')),
    created_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_supplement_payment_links_status ON supplement_payment_links(status, expires_at_utc);
