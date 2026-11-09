-- ==============================================================================
-- GYM VIP (VIP CENTER FIT) — MIGRACIÓN FLYWAY V13
-- Recuperación de contraseña segura y códigos de barras para catálogo administrativo
-- ==============================================================================

-- 1. Tokens de recuperación de contraseña de un solo uso (SHA-256)
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_password_reset_account_id ON password_reset_tokens(account_id);

-- 2. Soporte de código de barras para escaneo de suplementos (HU-23 / HU-24 / Admin Scanner)
ALTER TABLE supplements ADD COLUMN IF NOT EXISTS barcode VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_supplements_barcode ON supplements(barcode);

-- Asignar códigos de barras de ejemplo a suplementos existentes para pruebas de escaneo
UPDATE supplements
SET barcode = '7751234567890'
WHERE barcode IS NULL AND lower(name) LIKE '%whey%' OR lower(name) LIKE '%proteina%';

UPDATE supplements
SET barcode = '7759876543210'
WHERE barcode IS NULL AND lower(name) LIKE '%creatina%';
