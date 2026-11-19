-- Mantiene el contenido publicado mientras una edición espera aprobación.
ALTER TABLE gym_faqs ADD COLUMN revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE gym_faqs ADD COLUMN proposal_question VARCHAR(300);
ALTER TABLE gym_faqs ADD COLUMN proposal_answer TEXT;
ALTER TABLE gym_faqs ADD COLUMN proposal_category VARCHAR(100);
ALTER TABLE gym_faqs ADD COLUMN updated_at_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE gym_faqs ADD CONSTRAINT faq_proposal_complete CHECK (
    (proposal_question IS NULL AND proposal_answer IS NULL AND proposal_category IS NULL)
    OR (proposal_question IS NOT NULL AND proposal_answer IS NOT NULL AND proposal_category IS NOT NULL)
);
