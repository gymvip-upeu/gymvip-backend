ALTER TABLE gym_services ADD COLUMN status_changed_at_utc TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE gym_services ADD COLUMN archived_at_utc TIMESTAMP WITHOUT TIME ZONE;
UPDATE gym_services SET status_changed_at_utc = COALESCE(updated_at_utc, CURRENT_TIMESTAMP)
WHERE status_changed_at_utc IS NULL;

CREATE TABLE facility_status_history (
    id UUID PRIMARY KEY,
    facility_id UUID NOT NULL REFERENCES gym_services(id),
    previous_status VARCHAR(40),
    next_status VARCHAR(40) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    reason VARCHAR(500),
    changed_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_facility_status_history_facility ON facility_status_history(facility_id, changed_at_utc DESC);
