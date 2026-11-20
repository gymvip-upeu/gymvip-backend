-- V20: Pedidos de Suplementos, Carrito Persistido, Verificación de Pagos y Tickets de Retiro QR

-- 1. Carrito de Compras Persistido por Cuenta
CREATE TABLE IF NOT EXISTS member_carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_member_carts_account ON member_carts(account_id);

CREATE TABLE IF NOT EXISTS member_cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES member_carts(id) ON DELETE CASCADE,
    supplement_id UUID NOT NULL REFERENCES supplements(id) ON DELETE CASCADE,
    quantity INT NOT NULL CHECK (quantity > 0 AND quantity <= 20),
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_supplement UNIQUE(cart_id, supplement_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_items_lookup ON member_cart_items(cart_id, supplement_id);

-- 2. Pedidos de Suplementos (Ciclo de Vida de Extremo a Extremo)
CREATE TABLE IF NOT EXISTS supplement_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(30) NOT NULL UNIQUE,
    client_id UUID NOT NULL REFERENCES clients(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    order_status VARCHAR(40) NOT NULL DEFAULT 'CREADO'
        CHECK (order_status IN ('CREADO', 'PENDIENTE_PAGO', 'PAGADO_POR_VERIFICAR', 'CONFIRMADO_PREPARACION', 'LISTO_PARA_RETIRO', 'ENTREGADO', 'CANCELADO', 'RECHAZADO', 'EXPIRADO', 'DEVUELTO')),
    payment_status VARCHAR(40) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (payment_status IN ('PENDIENTE', 'EVIDENCIA_SUBIDA', 'VERIFICADO', 'RECHAZADO', 'DEVUELTO')),
    fulfillment_status VARCHAR(40) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (fulfillment_status IN ('PENDIENTE', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'ANULADO')),
    subtotal_pen NUMERIC(12,2) NOT NULL CHECK (subtotal_pen >= 0),
    discount_pen NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (discount_pen >= 0),
    total_pen NUMERIC(12,2) NOT NULL CHECK (total_pen >= 0),
    payment_method VARCHAR(40) NOT NULL DEFAULT 'TRANSFERENCIA_MANUAL'
        CHECK (payment_method IN ('TRANSFERENCIA_MANUAL', 'YAPE', 'PLIN', 'EFECTIVO_MOSTRADOR', 'TARJETA_PASARELA')),
    payment_reference VARCHAR(100) NULL,
    payment_evidence_url VARCHAR(500) NULL,
    payment_evidence_notes VARCHAR(500) NULL,
    payment_evidence_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    verified_by VARCHAR(150) NULL,
    verified_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    verified_amount_pen NUMERIC(12,2) NULL,
    rejection_reason VARCHAR(500) NULL,
    cancellation_reason VARCHAR(500) NULL,
    reservation_expires_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    stock_reserved BOOLEAN NOT NULL DEFAULT FALSE,
    stock_released BOOLEAN NOT NULL DEFAULT FALSE,
    pickup_token VARCHAR(120) NULL UNIQUE,
    pickup_token_issued_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    pickup_token_consumed_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    fulfilled_by VARCHAR(150) NULL,
    fulfilled_at_utc TIMESTAMP WITHOUT TIME ZONE NULL,
    customer_notes VARCHAR(500) NULL,
    idempotency_key VARCHAR(120) NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_client ON supplement_orders(client_id, created_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status ON supplement_orders(order_status, payment_status, fulfillment_status);
CREATE INDEX IF NOT EXISTS idx_orders_pickup_token ON supplement_orders(pickup_token) WHERE pickup_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_reservation ON supplement_orders(stock_reserved, reservation_expires_at_utc) WHERE stock_reserved = TRUE AND stock_released = FALSE;

-- 3. Detalle de Ítems del Pedido (Snapshot Inmutable de Precios y Promociones)
CREATE TABLE IF NOT EXISTS supplement_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES supplement_orders(id) ON DELETE CASCADE,
    supplement_id UUID NOT NULL REFERENCES supplements(id),
    product_name VARCHAR(150) NOT NULL,
    product_brand VARCHAR(120) NOT NULL,
    product_presentation VARCHAR(120) NOT NULL,
    product_flavor VARCHAR(100) NULL,
    unit_regular_price_pen NUMERIC(12,2) NOT NULL CHECK (unit_regular_price_pen >= 0),
    unit_final_price_pen NUMERIC(12,2) NOT NULL CHECK (unit_final_price_pen >= 0),
    applied_promotion_id UUID NULL,
    applied_promotion_name VARCHAR(160) NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    line_total_pen NUMERIC(12,2) NOT NULL CHECK (line_total_pen >= 0)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON supplement_order_items(order_id);

-- 4. Registro de Auditoría de Cambios de Estado del Pedido
CREATE TABLE IF NOT EXISTS supplement_order_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES supplement_orders(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    from_order_status VARCHAR(40) NULL,
    to_order_status VARCHAR(40) NULL,
    actor VARCHAR(150) NOT NULL,
    details VARCHAR(1000) NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_audit_order ON supplement_order_audit(order_id, created_at_utc DESC);
