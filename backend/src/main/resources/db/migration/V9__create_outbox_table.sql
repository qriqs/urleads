-- V9__create_outbox_table.sql
-- Tabla outbox para publicación fiable de eventos de dominio CRM (Backend V2 C04)

CREATE TABLE outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    exchange VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT chk_outbox_event_id_not_blank CHECK (event_id ~ '\S'),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT chk_outbox_retry_count_non_negative CHECK (retry_count >= 0)
);

CREATE INDEX idx_outbox_status_next_retry ON outbox (status, next_retry_at);
CREATE INDEX idx_outbox_created_at ON outbox (created_at);
