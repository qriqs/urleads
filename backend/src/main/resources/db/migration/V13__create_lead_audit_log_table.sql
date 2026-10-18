-- V13__create_lead_audit_log_table.sql
-- Tabla de auditoría independiente de notas para registrar deltas de cambios en leads (Backend V2 C09)

CREATE TABLE lead_audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lead_audit_log_lead FOREIGN KEY (lead_id) REFERENCES lead (id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_audit_log_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT chk_lead_audit_log_field_not_blank CHECK (field_name ~ '\S')
);

CREATE INDEX idx_lead_audit_log_lead_user ON lead_audit_log (lead_id, usuario_id, created_at DESC, id DESC);
CREATE INDEX idx_lead_audit_log_created_at ON lead_audit_log (created_at);
