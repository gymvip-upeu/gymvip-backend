CREATE TABLE operational_incidents (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABIERTA' CHECK (status IN ('ABIERTA', 'CERRADA')),
    reported_by VARCHAR(150) NOT NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    closed_by VARCHAR(150),
    closed_at_utc TIMESTAMP WITHOUT TIME ZONE,
    close_reason VARCHAR(1000),
    CONSTRAINT chk_incident_close_details CHECK (
        (status = 'ABIERTA' AND closed_by IS NULL AND closed_at_utc IS NULL AND close_reason IS NULL)
        OR (status = 'CERRADA' AND closed_by IS NOT NULL AND closed_at_utc IS NOT NULL AND close_reason IS NOT NULL)
    )
);
CREATE INDEX idx_operational_incidents_status_created ON operational_incidents(status, created_at_utc DESC);
