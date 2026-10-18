-- V8__create_user_preferences_table.sql
-- Tabla para almacenar preferencias de usuario (zona horaria predeterminada, horario laboral y canales de notificación)
CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    zona_horaria VARCHAR(64) NOT NULL DEFAULT 'America/Lima',
    inicio_jornada TIME NOT NULL DEFAULT '09:00:00',
    fin_jornada TIME NOT NULL DEFAULT '18:00:00',
    dias_laborales VARCHAR(64) NOT NULL DEFAULT '1,2,3,4,5',
    notificaciones_habilitadas BOOLEAN NOT NULL DEFAULT TRUE,
    canal_websocket BOOLEAN NOT NULL DEFAULT TRUE,
    canal_email BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_preferences_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_preferences_usuario UNIQUE (usuario_id),
    CONSTRAINT chk_jornada_fin_despues_inicio CHECK (fin_jornada > inicio_jornada)
);

CREATE INDEX IF NOT EXISTS idx_user_preferences_usuario ON user_preferences (usuario_id);
