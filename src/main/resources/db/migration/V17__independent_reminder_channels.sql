ALTER TABLE reminder_configurations ADD COLUMN post_expiry_days_selected VARCHAR(50);
UPDATE reminder_configurations SET post_expiry_days_selected =
    (SELECT string_agg(n::text, ',' ORDER BY n) FROM generate_series(1, post_expiry_days) n);
ALTER TABLE reminder_configurations ALTER COLUMN post_expiry_days_selected SET NOT NULL;
ALTER TABLE sent_reminder_dispatches ADD COLUMN title VARCHAR(200);
ALTER TABLE sent_reminder_dispatches ADD COLUMN message TEXT;
ALTER TABLE sent_reminder_dispatches ADD COLUMN template_name VARCHAR(80);
ALTER TABLE sent_reminder_dispatches ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_reminder_app_inbox ON sent_reminder_dispatches(client_id, created_at DESC) WHERE channel='APP';
