-- Preserve the commercial policy accepted with each new period.
-- NULL means a legacy record whose original terms cannot be inferred.
ALTER TABLE membership_periods ADD COLUMN policy_text TEXT;
