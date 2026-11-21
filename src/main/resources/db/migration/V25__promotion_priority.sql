ALTER TABLE supplement_promotions ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_supplement_promotion_priority
    ON supplement_promotions(supplement_id, priority DESC, id);
