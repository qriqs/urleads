-- V4__create_processed_event.sql
-- Tabla de deduplicacion de eventos procesados para consumidores idempotentes (RabbitMQ)
CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_event_type ON processed_event (event_type);

-- Asegurar longitud de event_id en notification hasta 100 caracteres
ALTER TABLE notification ALTER COLUMN event_id TYPE VARCHAR(100);
