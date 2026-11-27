ALTER TABLE organization_requests
    ADD COLUMN fiscal_status VARCHAR(24),
    ADD COLUMN fiscal_condition VARCHAR(24),
    ADD COLUMN verified_business_name VARCHAR(200),
    ADD COLUMN representative_evidence VARCHAR(240),
    ADD COLUMN verified_contact_at TIMESTAMPTZ,
    ADD COLUMN fiscal_reviewed_at TIMESTAMPTZ,
    ADD COLUMN fiscal_reviewed_by VARCHAR(150);
