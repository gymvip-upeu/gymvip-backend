-- S1 Alexander: extend the reserved contracts/payments; never rewrite V1/V2.
ALTER TABLE memberships ADD COLUMN service_code VARCHAR(30) NOT NULL DEFAULT 'GYM';
CREATE UNIQUE INDEX uk_memberships_client_service ON memberships(client_id, service_code);
ALTER TABLE memberships ADD CONSTRAINT ck_membership_dates CHECK (end_date > start_date);

CREATE TABLE membership_periods (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL REFERENCES memberships(id),
    plan_version_id UUID NOT NULL REFERENCES plan_versions(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    CONSTRAINT ck_period_dates CHECK (end_date > start_date),
    CONSTRAINT uk_period_membership_id UNIQUE (membership_id, id),
    CONSTRAINT uk_period_start UNIQUE (membership_id, start_date)
);
-- Preserve any legacy reserved-table records; ambiguous legacy payments are not guessed.
INSERT INTO membership_periods(id, membership_id, plan_version_id, start_date, end_date, created_at)
SELECT id, id, plan_version_id, start_date, end_date, created_at FROM memberships;

ALTER TABLE payment_records ADD COLUMN period_id UUID;
ALTER TABLE payment_records ADD CONSTRAINT fk_payment_period
    FOREIGN KEY (membership_id, period_id) REFERENCES membership_periods(membership_id, id);
CREATE UNIQUE INDEX uk_payment_period ON payment_records(period_id);
CREATE INDEX idx_period_coverage ON membership_periods(membership_id, start_date, end_date);

CREATE TABLE membership_operations (
    actor_id UUID NOT NULL REFERENCES accounts(id),
    operation_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    PRIMARY KEY (actor_id, operation_key)
);
