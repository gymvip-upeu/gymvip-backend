-- ==============================================================================
-- GYM VIP (VIP CENTER FIT) — MIGRACIÓN INICIAL FLYWAY V1
-- Motor: PostgreSQL / H2 en compatibilidad PostgreSQL
-- ==============================================================================

-- 1. Cuentas de personal y autenticación (HU-04)
CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- 2. Roles asignados a cuentas (HU-04)
CREATE TABLE IF NOT EXISTS account_roles (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    role_id VARCHAR(50) NOT NULL,
    PRIMARY KEY (account_id, role_id)
);

-- 3. Sesiones activas y tokens (HU-03 / HU-04)
CREATE TABLE IF NOT EXISTS sessions (
    token VARCHAR(128) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    user_agent VARCHAR(255),
    ip_address VARCHAR(50)
);

-- 4. Ficha de clientes / socios (HU-01)
CREATE TABLE IF NOT EXISTS clients (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    email VARCHAR(150),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone VARCHAR(50),
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    account_id UUID REFERENCES accounts(id),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- 5. Documentos de identidad y unicidad compuesta (HU-01)
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    country_code VARCHAR(10) NOT NULL DEFAULT 'PE',
    document_type VARCHAR(30) NOT NULL,
    document_number VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_document_country_type_num UNIQUE (country_code, document_type, document_number)
);

-- 6. Registro de consentimientos legales y avisos (Ley N° 29733) (HU-01)
CREATE TABLE IF NOT EXISTS consent_records (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    policy_type VARCHAR(50) NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    choice BOOLEAN NOT NULL,
    channel VARCHAR(50) NOT NULL,
    ip_address VARCHAR(50),
    timestamp_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 7. Catálogo de planes de membresía (HU-06)
CREATE TABLE IF NOT EXISTS plans (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    duration_days INT NOT NULL CHECK (duration_days > 0),
    price_pen NUMERIC(12,2) NOT NULL CHECK (price_pen > 0),
    benefits VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

-- 8. Versiones históricas de planes (HU-06)
CREATE TABLE IF NOT EXISTS plan_versions (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    duration_days INT NOT NULL CHECK (duration_days > 0),
    price_pen NUMERIC(12,2) NOT NULL CHECK (price_pen > 0),
    benefits VARCHAR(1000),
    effective_from TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 9. Membresías contratadas (tabla reservada para extensión en S1 por compañeros)
CREATE TABLE IF NOT EXISTS memberships (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    plan_version_id UUID NOT NULL REFERENCES plan_versions(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'VIGENTE',
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 10. Registros de cobro presencial (tabla reservada para extensión en S1 por compañeros)
CREATE TABLE IF NOT EXISTS payment_records (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL REFERENCES memberships(id),
    amount_pen NUMERIC(12,2) NOT NULL CHECK (amount_pen > 0),
    currency VARCHAR(10) NOT NULL DEFAULT 'PEN',
    payment_method VARCHAR(50) NOT NULL,
    external_reference VARCHAR(100),
    registered_by_account_id UUID REFERENCES accounts(id),
    payment_timestamp_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    notes VARCHAR(500)
);

-- 11. Eventos de auditoría de operaciones (Audit Log)
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    actor_email VARCHAR(150),
    action VARCHAR(100) NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    details VARCHAR(2000),
    timestamp_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- Índices de consulta frecuente
CREATE INDEX IF NOT EXISTS idx_clients_search ON clients (last_name, first_name);
CREATE INDEX IF NOT EXISTS idx_documents_lookup ON documents (country_code, document_type, document_number);
CREATE INDEX IF NOT EXISTS idx_plans_status ON plans (status);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_events (timestamp_utc);
