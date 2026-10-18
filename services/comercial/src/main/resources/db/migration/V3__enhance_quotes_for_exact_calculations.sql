-- V3__enhance_quotes_for_exact_calculations.sql
-- Agregar soporte para subtotal, impuesto (tax) y tasa de impuesto en cotizaciones
-- y permitir cantidades decimales fraccionarias en líneas de cotización

ALTER TABLE quotes ADD COLUMN IF NOT EXISTS subtotal NUMERIC(15, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS tax NUMERIC(15, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5, 4) NOT NULL DEFAULT 0.1800;

ALTER TABLE quote_items ALTER COLUMN quantity TYPE NUMERIC(12, 2);
