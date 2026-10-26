-- Jorge S1: preserve V1-V4 and add concurrent client editing / protected history.
ALTER TABLE clients ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE client_changes (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL,
    client_id UUID NOT NULL REFERENCES clients(id),
    actor_email VARCHAR(150) NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    previous_value VARCHAR(150),
    new_value VARCHAR(150),
    changed_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
CREATE INDEX idx_client_changes_history ON client_changes(client_id, changed_at_utc DESC);
