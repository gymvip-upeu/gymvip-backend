-- ==============================================================================
-- GYM VIP (VIP CENTER FIT) — MIGRACIÓN FLYWAY V2
-- HU-01: Código de socio alfanumérico único para clientes (ej. MAR-8Q2L7K / VIP-8Q2L7K)
-- HU-06: Restricción de concurrencia e inmutabilidad en versiones de planes
-- ==============================================================================

-- 1. Soporte para código de socio único en ficha de clientes (HU-01)
ALTER TABLE clients ADD COLUMN IF NOT EXISTS member_code VARCHAR(30);

-- Actualizar registros existentes que no tengan código generado
UPDATE clients 
SET member_code = 'VIP-' || UPPER(SUBSTR(REPLACE(CAST(id AS VARCHAR(36)), '-', ''), 1, 6)) 
WHERE member_code IS NULL;

-- Restricción UNIQUE e índice para member_code
CREATE UNIQUE INDEX IF NOT EXISTS uk_clients_member_code ON clients (member_code);

-- 2. Restricción de unicidad compuesta para versiones históricas de planes (HU-06)
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_versions_plan_version ON plan_versions (plan_id, version_number);
