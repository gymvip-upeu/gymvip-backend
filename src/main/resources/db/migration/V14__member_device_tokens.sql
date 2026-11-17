-- V14: Registro seguro de dispositivos y tokens FCM para notificaciones Push (HU-15, HU-34)
CREATE TABLE IF NOT EXISTS member_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    device_token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL DEFAULT 'ANDROID', -- ANDROID, IOS, DESKTOP
    device_model VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_member_device_token UNIQUE (client_id, device_token)
);

CREATE INDEX IF NOT EXISTS idx_member_device_tokens_client ON member_device_tokens(client_id);
