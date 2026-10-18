-- V2__add_owner_to_catalog_items.sql
-- Agregar columna owner_id para aislamiento estricto por usuario en el catalogo

ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS owner_id VARCHAR(50) NOT NULL DEFAULT '42';

-- El codigo de producto es unico por propietario
ALTER TABLE catalog_items DROP CONSTRAINT IF EXISTS catalog_items_code_key;
ALTER TABLE catalog_items ADD CONSTRAINT uk_catalog_items_owner_code UNIQUE(owner_id, code);

CREATE INDEX IF NOT EXISTS idx_catalog_items_owner_id ON catalog_items(owner_id);
