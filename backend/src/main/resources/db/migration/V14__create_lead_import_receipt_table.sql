-- Backend V2 C10: Tabla de recibos durables de importación de leads para idempotencia
CREATE TABLE lead_import_receipt (
    id BIGSERIAL PRIMARY KEY,
    preview_id VARCHAR(255) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    file_hash VARCHAR(64) NOT NULL,
    row_count INT NOT NULL,
    lead_ids TEXT NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lead_import_receipt_preview_id UNIQUE (preview_id)
);

CREATE INDEX idx_lead_import_receipt_usuario ON lead_import_receipt(usuario_id);
