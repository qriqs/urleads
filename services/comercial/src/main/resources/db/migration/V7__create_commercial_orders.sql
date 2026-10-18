-- V7: Soporte para órdenes comerciales generadas a partir de cotizaciones aceptadas
CREATE TABLE commercial_orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(32) NOT NULL UNIQUE,
    quote_id BIGINT NOT NULL UNIQUE REFERENCES quotes(id),
    lead_id BIGINT NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(14, 2) NOT NULL,
    tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0000,
    tax NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    total NUMERIC(14, 2) NOT NULL,
    notes TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE commercial_order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES commercial_orders(id) ON DELETE CASCADE,
    catalog_item_id BIGINT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    quantity NUMERIC(12, 2) NOT NULL,
    unit_price NUMERIC(14, 2) NOT NULL,
    subtotal NUMERIC(14, 2) NOT NULL
);

CREATE INDEX idx_commercial_orders_owner_id ON commercial_orders(owner_id);
CREATE INDEX idx_commercial_orders_lead_id ON commercial_orders(lead_id);
CREATE INDEX idx_commercial_orders_quote_id ON commercial_orders(quote_id);
CREATE INDEX idx_commercial_order_items_order_id ON commercial_order_items(order_id);
