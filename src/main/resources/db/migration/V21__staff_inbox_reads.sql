-- Personal without a client record reads general announcements by account.
CREATE TABLE IF NOT EXISTS staff_inbox_reads (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    item_id VARCHAR(120) NOT NULL,
    read_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_staff_inbox_read UNIQUE (account_id, item_id)
);
CREATE INDEX IF NOT EXISTS idx_staff_inbox_reads_account ON staff_inbox_reads(account_id);
