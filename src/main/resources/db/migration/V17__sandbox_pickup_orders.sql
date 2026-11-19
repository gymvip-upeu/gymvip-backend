CREATE TABLE sandbox_pickup_orders (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    request_id UUID NOT NULL,
    supplement_id UUID NOT NULL REFERENCES supplements(id),
    product_name VARCHAR(120) NOT NULL,
    unit_price_pen NUMERIC(12,2) NOT NULL CHECK (unit_price_pen > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    provider_order_id VARCHAR(100) UNIQUE,
    checkout_url TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (status IN ('PENDIENTE', 'PAGADO_PRUEBA', 'RECHAZADO', 'CANCELADO', 'VENCIDO', 'REEMBOLSADO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (client_id, request_id)
);
CREATE INDEX sandbox_pickup_orders_client ON sandbox_pickup_orders (client_id, created_at DESC);
