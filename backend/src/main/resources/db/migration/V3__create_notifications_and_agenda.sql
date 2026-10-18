-- V3__create_notifications_and_agenda.sql
-- Tabla de notificaciones persistentes, deduplicadas y posponibles
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    event_id VARCHAR(64) UNIQUE,
    tipo VARCHAR(64) NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    mensaje TEXT NOT NULL,
    data TEXT,
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    leida_en TIMESTAMP WITH TIME ZONE,
    pospuesta_hasta TIMESTAMP WITH TIME ZONE,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notification_usuario_creado ON notification (usuario_id, creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_notification_usuario_leida ON notification (usuario_id, leida);

-- Tabla de tareas de agenda vinculadas a usuario y lead
CREATE TABLE IF NOT EXISTS agenda_tarea (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    lead_id BIGINT,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    fecha_limite DATE,
    hora_limite TIME,
    estado VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE',
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tarea_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_tarea_lead FOREIGN KEY (lead_id) REFERENCES lead (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_agenda_tarea_usuario_fecha ON agenda_tarea (usuario_id, fecha_limite);

-- Tabla de recordatorios durables con ocurrencias únicas
CREATE TABLE IF NOT EXISTS recordatorio (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurrence_id VARCHAR(64) UNIQUE NOT NULL,
    usuario_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    programado_para TIMESTAMP WITH TIME ZONE NOT NULL,
    estado VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE',
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recordatorio_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_recordatorio_estado_programado ON recordatorio (estado, programado_para);
