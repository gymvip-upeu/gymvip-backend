-- ==============================================================================
-- GYM VIP ENTERPRISE — MIGRACIÓN FLYWAY V32
-- Expansión empresarial aditiva (sin alterar V31):
-- Sedes multitenant, RRHH y Turnos, Stock por Sede, Activos/Equipos,
-- Proveedores y Compras, Cajas y Movimientos, y Fidelización opt-in.
-- ==============================================================================

-- 1. Vínculo multi-sede progresivo sobre entidades existentes (no destructivo)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS is_seed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE facilities ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE supplements ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE supplement_orders ADD COLUMN IF NOT EXISTS branch_id UUID;

UPDATE clients SET branch_id = 'b0000000-0000-0000-0000-000000000001'
WHERE branch_id IS NULL AND EXISTS (SELECT 1 FROM branches WHERE id = 'b0000000-0000-0000-0000-000000000001');

-- 2. Recursos Humanos: fichas operativas y turnos programados (EMP-03)
CREATE TABLE IF NOT EXISTS staff_profiles (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    organization_id UUID REFERENCES organizations(id),
    branch_id UUID REFERENCES branches(id),
    job_title VARCHAR(100) NOT NULL,
    document_number VARCHAR(20),
    hire_date DATE,
    shift_pattern VARCHAR(120) DEFAULT 'Lunes a Sábado 06:00-14:00',
    specialty VARCHAR(160),
    emergency_contact VARCHAR(160),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS staff_shifts (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id),
    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROGRAMADO',
    notes VARCHAR(400),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 3. Inventario multi-sede y Kardex real (EMP-06)
CREATE TABLE IF NOT EXISTS branch_supplement_inventory (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    supplement_id UUID NOT NULL REFERENCES supplements(id) ON DELETE CASCADE,
    stock_available INT NOT NULL DEFAULT 0,
    stock_reserved INT NOT NULL DEFAULT 0,
    min_stock_threshold INT NOT NULL DEFAULT 5,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_branch_supplement UNIQUE (branch_id, supplement_id)
);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES branches(id),
    supplement_id UUID NOT NULL REFERENCES supplements(id),
    movement_type VARCHAR(30) NOT NULL, -- INGRESO, SALIDA, AJUSTE, COMPRA
    quantity_delta INT NOT NULL,
    stock_after INT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    actor_email VARCHAR(150) NOT NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 4. Activos, Máquinas y Equipos del Gimnasio (EMP-07)
CREATE TABLE IF NOT EXISTS gym_equipment_assets (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    branch_id UUID REFERENCES branches(id),
    asset_code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(80) NOT NULL,
    brand VARCHAR(80),
    serial_number VARCHAR(100),
    zone_location VARCHAR(120),
    operational_status VARCHAR(40) NOT NULL DEFAULT 'OPERATIVO', -- OPERATIVO, MANTENIMIENTO, FUERA_DE_SERVICIO, BAJA
    last_maintenance_date DATE,
    next_maintenance_date DATE,
    notes VARCHAR(600),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 5. Proveedores y Órdenes de Compra (EMP-08)
CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    ruc VARCHAR(20) NOT NULL UNIQUE,
    business_name VARCHAR(180) NOT NULL,
    category VARCHAR(80) NOT NULL,
    contact_name VARCHAR(120),
    phone VARCHAR(30),
    email VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    organization_id UUID REFERENCES organizations(id),
    branch_id UUID REFERENCES branches(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    status VARCHAR(30) NOT NULL DEFAULT 'BORRADOR', -- BORRADOR, APROBADA, RECIBIDA, ANULADA
    total_pen NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    created_by VARCHAR(150) NOT NULL,
    received_by VARCHAR(150),
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    received_at_utc TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    supplement_id UUID REFERENCES supplements(id),
    item_description VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_cost_pen NUMERIC(12,2) NOT NULL,
    subtotal_pen NUMERIC(12,2) NOT NULL
);

-- 6. Control de Cajas y Movimientos Financieros (EMP-09)
CREATE TABLE IF NOT EXISTS cash_registers (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    branch_id UUID REFERENCES branches(id),
    opened_by VARCHAR(150) NOT NULL,
    closed_by VARCHAR(150),
    opening_amount_pen NUMERIC(12,2) NOT NULL DEFAULT 0,
    expected_closing_pen NUMERIC(12,2) NOT NULL DEFAULT 0,
    declared_closing_pen NUMERIC(12,2),
    status VARCHAR(20) NOT NULL DEFAULT 'ABIERTA', -- ABIERTA, CERRADA
    notes VARCHAR(400),
    opened_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    closed_at_utc TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS cash_movements (
    id UUID PRIMARY KEY,
    cash_register_id UUID NOT NULL REFERENCES cash_registers(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id),
    movement_type VARCHAR(20) NOT NULL, -- INGRESO, EGRESO
    source_module VARCHAR(40) NOT NULL, -- MEMBRESIA, SUPLEMENTO, CAJA_MANUAL
    amount_pen NUMERIC(12,2) NOT NULL,
    payment_method VARCHAR(40) NOT NULL DEFAULT 'EFECTIVO',
    concept VARCHAR(250) NOT NULL,
    reference_id VARCHAR(120),
    actor_email VARCHAR(150) NOT NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 7. Programa de Puntos / Fidelización Opt-in (EMP-13)
CREATE TABLE IF NOT EXISTS loyalty_balances (
    client_id UUID PRIMARY KEY REFERENCES clients(id) ON DELETE CASCADE,
    enrolled BOOLEAN NOT NULL DEFAULT FALSE,
    points_balance INT NOT NULL DEFAULT 0,
    lifetime_points INT NOT NULL DEFAULT 0,
    tier_level VARCHAR(30) NOT NULL DEFAULT 'BRONCE',
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS loyalty_transactions (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    points_delta INT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    actor_email VARCHAR(150),
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);
