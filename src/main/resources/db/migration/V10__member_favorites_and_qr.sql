-- V10: Favoritos del Socio en Catálogo (HU-41) y Tokens de Credencial QR Dinámica (HU-13)

CREATE TABLE IF NOT EXISTS member_favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    product_id VARCHAR(100) NOT NULL,
    product_title VARCHAR(200) NOT NULL,
    product_category VARCHAR(100) NOT NULL,
    notes VARCHAR(250),
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_member_favorite_product UNIQUE (client_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_member_favorites_client ON member_favorites(client_id);

CREATE TABLE IF NOT EXISTS member_dynamic_qr_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at_utc TIMESTAMP WITH TIME ZONE,
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_member_qr_token ON member_dynamic_qr_tokens(token);
