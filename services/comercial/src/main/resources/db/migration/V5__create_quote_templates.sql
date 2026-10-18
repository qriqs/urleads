-- V5: Creación de tablas para plantillas de cotización por usuario
CREATE TABLE quote_templates (
    id BIGSERIAL PRIMARY KEY,
    owner_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    currency VARCHAR(3) NOT NULL,
    tax_rate NUMERIC(5, 4) NOT NULL DEFAULT 0.1800,
    notes TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quote_templates_owner_id ON quote_templates(owner_id);

CREATE TABLE quote_template_items (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES quote_templates(id) ON DELETE CASCADE,
    catalog_item_id BIGINT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    quantity NUMERIC(12, 2) NOT NULL DEFAULT 1.00,
    unit_price NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    subtotal NUMERIC(15, 2) NOT NULL DEFAULT 0.00
);

CREATE INDEX idx_quote_template_items_template_id ON quote_template_items(template_id);
