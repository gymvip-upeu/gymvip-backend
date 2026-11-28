ALTER TABLE operational_incidents
    ADD COLUMN organization_id UUID REFERENCES organizations(id),
    ADD COLUMN branch_id UUID REFERENCES branches(id);

UPDATE operational_incidents i
SET organization_id = COALESCE(a.organization_id, '11111111-1111-4111-8111-111111111111'::uuid),
    branch_id = b.id
FROM accounts a LEFT JOIN branches b ON b.id = a.primary_branch_id
WHERE a.email = i.reported_by;

-- La bitácora original correspondía al gimnasio inicial. Los actores históricos
-- sin cuenta se mantienen en el ámbito de empresa y no se atribuyen a una sede.
UPDATE operational_incidents
SET organization_id = '11111111-1111-4111-8111-111111111111'::uuid
WHERE organization_id IS NULL;

UPDATE operational_incidents i
SET branch_id = NULL
FROM branches b
WHERE b.id = i.branch_id AND b.organization_id <> i.organization_id;

ALTER TABLE gym_announcements ADD COLUMN draft_branch_id UUID REFERENCES branches(id);

ALTER TABLE operational_incidents ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX idx_incidents_org_branch_created
    ON operational_incidents(organization_id, branch_id, created_at_utc DESC);
