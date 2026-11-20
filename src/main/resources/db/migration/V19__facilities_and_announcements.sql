-- V19: Ampliación de Gestión de Instalaciones (Áreas VIP), Bandeja Canónica de Comunicados y Eventos Outbox (HU-05, HU-18, HU-34)

-- 1. Ampliación de la tabla gym_services para control operativo real
ALTER TABLE gym_services
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'DISPONIBLE',
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS included_plan_types VARCHAR(200) NOT NULL DEFAULT 'TODOS',
    ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_gym_services_status ON gym_services(status, is_archived);

-- 2. Tabla canónica de comunicados y avisos administrables del club
CREATE TABLE IF NOT EXISTS gym_announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'COMUNICADO', -- COMUNICADO, MANTENIMIENTO, INSTALACION, HORARIO
    target_audience VARCHAR(50) NOT NULL DEFAULT 'TODOS', -- TODOS, ACTIVOS, VENCIDOS
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    facility_id UUID REFERENCES gym_services(id) ON DELETE SET NULL,
    created_by VARCHAR(100),
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gym_announcements_active ON gym_announcements(is_active, created_at_utc);

-- 3. Tabla transaccional Outbox para notificaciones persistentes y desacopladas (Push, WhatsApp, Email)
CREATE TABLE IF NOT EXISTS notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    channel VARCHAR(30) NOT NULL, -- PUSH, WHATSAPP, EMAIL
    recipient VARCHAR(150) NOT NULL,
    title VARCHAR(200),
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at_utc TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON notification_outbox(status, created_at_utc);

-- Inserción de comunicados canónicos iniciales del club
INSERT INTO gym_announcements (id, title, message, category, target_audience, is_active, created_by)
VALUES
    ('d0000001-0000-0000-0000-000000000001', 'Bienvenido a GYM VIP', 'Tu acceso y credencial digital están habilitados. Recuerda presentar tu QR en recepción para ingresar.', 'COMUNICADO', 'TODOS', true, 'ADMINISTRACION'),
    ('d0000001-0000-0000-0000-000000000002', 'Uso obligatorio de toalla personal', 'Por higiene y respeto mutuo entre miembros, el uso de toalla es obligatorio en máquinas y peso libre.', 'COMUNICADO', 'TODOS', true, 'ADMINISTRACION')
ON CONFLICT DO NOTHING;
