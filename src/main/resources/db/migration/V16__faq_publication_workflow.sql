-- Mantiene el contenido publicado mientras una edición espera aprobación.
ALTER TABLE gym_faqs ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE gym_faqs ADD COLUMN IF NOT EXISTS proposal_question VARCHAR(300);
ALTER TABLE gym_faqs ADD COLUMN IF NOT EXISTS proposal_answer TEXT;
ALTER TABLE gym_faqs ADD COLUMN IF NOT EXISTS proposal_category VARCHAR(100);
ALTER TABLE gym_faqs ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'faq_proposal_complete'
    ) THEN
        ALTER TABLE gym_faqs ADD CONSTRAINT faq_proposal_complete CHECK (
            (proposal_question IS NULL AND proposal_answer IS NULL AND proposal_category IS NULL)
            OR (proposal_question IS NOT NULL AND proposal_answer IS NOT NULL AND proposal_category IS NOT NULL)
        );
    END IF;
END $$;
