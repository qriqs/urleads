-- V5__create_appointments_table.sql
-- Tabla de citas de agenda con control de zona horaria y estados
CREATE TABLE IF NOT EXISTS agenda_cita (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    lead_id BIGINT,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    fin TIMESTAMP WITH TIME ZONE NOT NULL,
    zona_horaria VARCHAR(64) NOT NULL DEFAULT 'America/Lima',
    estado VARCHAR(32) NOT NULL DEFAULT 'PROGRAMADA',
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cita_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_cita_lead FOREIGN KEY (lead_id) REFERENCES lead (id) ON DELETE SET NULL,
    CONSTRAINT chk_cita_fin_despues_inicio CHECK (fin > inicio)
);

CREATE INDEX IF NOT EXISTS idx_agenda_cita_usuario_inicio ON agenda_cita (usuario_id, inicio);
CREATE INDEX IF NOT EXISTS idx_agenda_cita_usuario_estado ON agenda_cita (usuario_id, estado);
CREATE INDEX IF NOT EXISTS idx_agenda_cita_usuario_lead ON agenda_cita (usuario_id, lead_id);
