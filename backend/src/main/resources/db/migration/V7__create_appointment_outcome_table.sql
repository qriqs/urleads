-- V7__create_appointment_outcome_table.sql
-- Tabla para almacenar minutas, acuerdos y resultados estructurados de citas en Agenda
CREATE TABLE IF NOT EXISTS agenda_cita_minuta (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cita_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    resumen TEXT NOT NULL,
    acuerdos TEXT,
    proximas_acciones TEXT,
    resultado VARCHAR(64) NOT NULL DEFAULT 'REALIZADA',
    asistencia VARCHAR(64),
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_minuta_cita FOREIGN KEY (cita_id) REFERENCES agenda_cita (id) ON DELETE CASCADE,
    CONSTRAINT fk_minuta_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT uk_minuta_cita UNIQUE (cita_id)
);

CREATE INDEX IF NOT EXISTS idx_agenda_minuta_usuario ON agenda_cita_minuta (usuario_id);
CREATE INDEX IF NOT EXISTS idx_agenda_minuta_cita ON agenda_cita_minuta (cita_id);
