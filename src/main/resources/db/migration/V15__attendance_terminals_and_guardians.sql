-- ==============================================================================
-- V15: Terminales de Asistencia QR de Recepción, Trazabilidad, Apoderados y Stock
-- ==============================================================================

-- 1. Terminales autorizadas de recepción para emisión de QR temporal
CREATE TABLE attendance_terminals (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    venue_id UUID NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVA' CHECK (status IN ('ACTIVA', 'INACTIVA', 'REVOCADA')),
    current_operator VARCHAR(150) NULL,
    created_by VARCHAR(150) NOT NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_active_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    revoked_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    shared_secret VARCHAR(100) NOT NULL
);

CREATE INDEX idx_attendance_terminals_status ON attendance_terminals(status);

-- 2. Desafíos QR temporales generados por la terminal de recepción
CREATE TABLE attendance_terminal_challenges (
    id UUID PRIMARY KEY,
    terminal_id UUID NOT NULL REFERENCES attendance_terminals(id) ON DELETE CASCADE,
    challenge_token VARCHAR(250) NOT NULL UNIQUE,
    issued_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    expires_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_by VARCHAR(150) NOT NULL
);

CREATE INDEX idx_attendance_challenges_lookup ON attendance_terminal_challenges(challenge_token, expires_at_utc);
CREATE INDEX idx_attendance_challenges_terminal ON attendance_terminal_challenges(terminal_id, issued_at_utc DESC);

-- 3. Ampliación de member_attendance para registrar método y terminal
ALTER TABLE member_attendance ADD COLUMN IF NOT EXISTS terminal_id UUID NULL REFERENCES attendance_terminals(id);
ALTER TABLE member_attendance ADD COLUMN IF NOT EXISTS check_in_method VARCHAR(50) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE member_attendance ADD COLUMN IF NOT EXISTS request_id UUID NULL;
ALTER TABLE member_attendance ADD COLUMN IF NOT EXISTS audit_ref VARCHAR(200) NULL;

CREATE INDEX IF NOT EXISTS idx_member_attendance_date ON member_attendance(attendance_date DESC, checked_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_member_attendance_method ON member_attendance(check_in_method);

-- 4. Registro de auditoría de intentos rechazados de asistencia
CREATE TABLE attendance_rejection_logs (
    id UUID PRIMARY KEY,
    client_id UUID NULL REFERENCES clients(id) ON DELETE SET NULL,
    account_id UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    attempted_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    attendance_date DATE NOT NULL,
    terminal_id UUID NULL REFERENCES attendance_terminals(id) ON DELETE SET NULL,
    rejection_reason VARCHAR(100) NOT NULL,
    details VARCHAR(500) NULL,
    ip_address VARCHAR(45) NULL,
    recorded_by VARCHAR(150) NULL
);

CREATE INDEX idx_attendance_rejection_date ON attendance_rejection_logs(attendance_date DESC, attempted_at_utc DESC);
CREATE INDEX idx_attendance_rejection_client ON attendance_rejection_logs(client_id, attempted_at_utc DESC);

-- 5. Menores de edad y apoderados legales (Ley 29733 / D.S. 016-2024-JUS)
CREATE TABLE client_guardians (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    full_name VARCHAR(180) NOT NULL,
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('DNI', 'CE', 'PASAPORTE')),
    document_number VARCHAR(30) NOT NULL,
    relationship VARCHAR(50) NOT NULL, -- PADRE, MADRE, TUTOR_LEGAL, APODERADO
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(150) NULL,
    emergency_contact BOOLEAN NOT NULL DEFAULT TRUE,
    authorized_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    consent_reference VARCHAR(250) NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_by VARCHAR(150) NOT NULL,
    CONSTRAINT uk_client_guardian_doc UNIQUE(client_id, document_type, document_number)
);

CREATE INDEX idx_client_guardians_client ON client_guardians(client_id);

-- 6. Inventario y stock físico de suplementos
ALTER TABLE supplements ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0);
ALTER TABLE supplements ADD COLUMN IF NOT EXISTS min_stock INT NOT NULL DEFAULT 5 CHECK (min_stock >= 0);

CREATE TABLE supplement_stock_movements (
    id UUID PRIMARY KEY,
    supplement_id UUID NOT NULL REFERENCES supplements(id) ON DELETE CASCADE,
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    quantity INT NOT NULL,
    previous_stock INT NOT NULL CHECK (previous_stock >= 0),
    new_stock INT NOT NULL CHECK (new_stock >= 0),
    reason VARCHAR(250) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX idx_supplement_stock_mov ON supplement_stock_movements(supplement_id, created_at_utc DESC);

-- 7. Sembrado de terminal inicial de recepción para pruebas locales y producción
INSERT INTO attendance_terminals (
    id, name, venue_id, status, current_operator, created_by, created_at_utc, last_active_at_utc, revoked_at_utc, shared_secret
) VALUES (
    'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
    'Terminal Principal Recepción Mostrador 1',
    NULL,
    'INACTIVA',
    NULL,
    'sistema@gymvip.local',
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    'sec_terminal_rec_7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c'
) ON CONFLICT (id) DO NOTHING;
