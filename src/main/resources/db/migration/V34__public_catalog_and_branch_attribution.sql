-- The imported Lima examples need review before appearing in the commercial directory.
UPDATE branches
SET is_published = FALSE
WHERE organization_id = '11111111-1111-4111-8111-111111111111'
  AND updated_at IS NULL
  AND ((id = '22222222-2222-4222-8222-222222222221' AND address = 'Av. José Larco 740')
    OR (id = '22222222-2222-4222-8222-222222222222' AND address = 'Av. Aviación 2480'));

UPDATE organization_commercial_profiles
SET publish_status = 'BORRADOR', published_at_utc = NULL
WHERE organization_id = '11111111-1111-4111-8111-111111111111'
  AND updated_by = 'system@gymvip.pe';

-- Attribute only records with an existing and consistent organizational assignment.
UPDATE clients c
SET branch_id = b.id
FROM accounts a JOIN branches b
  ON b.id = a.primary_branch_id AND b.organization_id = a.organization_id
WHERE c.account_id = a.id AND c.branch_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_clients_report_branch_created ON clients(branch_id, created_at);
CREATE INDEX IF NOT EXISTS idx_supplement_orders_report_branch ON supplement_orders(branch_id, created_at_utc);
