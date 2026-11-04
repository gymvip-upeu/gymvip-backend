-- ==============================================================================
-- GYM VIP — Migración V9: Configuración de Recordatorios, Consentimientos y Trazabilidad (HU-14 y HU-18)
-- ==============================================================================

-- 1. Tabla de Configuración de Recordatorios Automáticos
CREATE TABLE IF NOT EXISTS reminder_configurations (
    id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    anticipation_days VARCHAR(50) NOT NULL DEFAULT '3,5,7,15',
    post_expiry_days INT NOT NULL DEFAULT 5,
    channels VARCHAR(50) NOT NULL DEFAULT 'WHATSAPP,APP',
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO reminder_configurations (id, enabled, anticipation_days, post_expiry_days, channels, updated_by)
VALUES ('11111111-1111-1111-1111-111111111111', TRUE, '3,5,7,15', 5, 'WHATSAPP,APP', 'sistema@vipcenterfit.pe')
ON CONFLICT (id) DO NOTHING;

-- 2. Columnas de Consentimiento por Canal en Socios
ALTER TABLE clients ADD COLUMN IF NOT EXISTS consent_whatsapp BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS consent_app BOOLEAN NOT NULL DEFAULT TRUE;

-- 3. Tabla de Despachos de Recordatorios Generados (Idempotencia)
CREATE TABLE IF NOT EXISTS sent_reminder_dispatches (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    client_id UUID NOT NULL REFERENCES clients(id),
    period_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    rule_type VARCHAR(30) NOT NULL,
    target_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SOLICITADO',
    external_delivery_id VARCHAR(100),
    error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sent_reminders_period ON sent_reminder_dispatches(period_id);
CREATE INDEX IF NOT EXISTS idx_sent_reminders_client ON sent_reminder_dispatches(client_id);

-- 4. Tabla de Auditoría de Cortes de Evaluación Ejecutados
CREATE TABLE IF NOT EXISTS evaluated_reminder_cuts (
    id UUID PRIMARY KEY,
    cut_date DATE NOT NULL,
    triggered_by VARCHAR(120) NOT NULL,
    eval_timestamp_utc TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    candidates_evaluated INT NOT NULL DEFAULT 0,
    notices_requested INT NOT NULL DEFAULT 0,
    notices_skipped INT NOT NULL DEFAULT 0
);
