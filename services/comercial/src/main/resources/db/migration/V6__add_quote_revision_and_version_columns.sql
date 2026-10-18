-- V6: Soporte para control de versiones, revisiones y bloqueo optimista
ALTER TABLE quotes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE quotes ADD COLUMN root_quote_id BIGINT;
ALTER TABLE quotes ADD COLUMN parent_quote_id BIGINT REFERENCES quotes(id);
ALTER TABLE quotes ADD COLUMN revision_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE quotes ADD COLUMN is_latest BOOLEAN NOT NULL DEFAULT TRUE;

-- Inicializar root_quote_id para registros existentes
UPDATE quotes SET root_quote_id = id WHERE root_quote_id IS NULL;

CREATE INDEX idx_quotes_root_quote_id ON quotes(root_quote_id);
CREATE INDEX idx_quotes_parent_quote_id ON quotes(parent_quote_id);
