ALTER TABLE sent_reminder_dispatches
    ADD COLUMN notice_title VARCHAR(160),
    ADD COLUMN notice_message VARCHAR(600);
