ALTER TABLE sent_reminder_dispatches
    ADD COLUMN IF NOT EXISTS notice_title VARCHAR(160),
    ADD COLUMN IF NOT EXISTS notice_message VARCHAR(600);
