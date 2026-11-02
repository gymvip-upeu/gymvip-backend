ALTER TABLE clients ADD COLUMN notes VARCHAR(2000);
ALTER TABLE accounts ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE membership_periods ADD COLUMN cancelled_at_utc TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE membership_periods ADD COLUMN cancellation_reason VARCHAR(500);
ALTER TABLE membership_periods ADD COLUMN cancelled_by VARCHAR(150);
CREATE TABLE member_attendance (
 id UUID PRIMARY KEY,
 client_id UUID NOT NULL REFERENCES clients(id),
 attendance_date DATE NOT NULL,
 checked_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
 recorded_by VARCHAR(150) NOT NULL,
 CONSTRAINT uk_member_attendance_day UNIQUE(client_id,attendance_date)
);
CREATE INDEX idx_member_attendance ON member_attendance(client_id,attendance_date DESC);
