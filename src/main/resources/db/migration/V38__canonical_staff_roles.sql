-- Keep both historical rows if an account already has the equivalent canonical role.
UPDATE account_roles AS assignment
SET role_id = mapping.canonical
FROM (VALUES ('ADMIN','ADMIN_EMPRESA'), ('GERENTE','GERENTE_GENERAL'), ('AUDITOR','AUDITOR_EMPRESA'))
    AS mapping(legacy, canonical)
WHERE assignment.role_id = mapping.legacy
  AND NOT EXISTS (
      SELECT 1 FROM account_roles existing
      WHERE existing.account_id = assignment.account_id AND existing.role_id = mapping.canonical
  );

UPDATE account_branch_assignments AS assignment
SET branch_role = mapping.canonical
FROM (VALUES ('ADMIN','ADMIN_EMPRESA'), ('GERENTE','GERENTE_GENERAL'), ('AUDITOR','AUDITOR_EMPRESA'))
    AS mapping(legacy, canonical)
WHERE assignment.branch_role = mapping.legacy
  AND NOT EXISTS (
      SELECT 1 FROM account_branch_assignments existing
      WHERE existing.account_id = assignment.account_id AND existing.branch_id = assignment.branch_id
        AND existing.branch_role = mapping.canonical
  );

-- Existing platform sessions expire so they authenticate under the new MFA policy.
UPDATE sessions
SET expires_at = LEAST(expires_at, NOW() AT TIME ZONE 'UTC')
WHERE account_id IN (SELECT account_id FROM account_roles WHERE role_id = 'SUPER_ADMIN');
