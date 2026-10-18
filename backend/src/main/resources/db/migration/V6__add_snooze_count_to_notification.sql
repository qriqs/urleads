-- V6__add_snooze_count_to_notification.sql
-- Columna snooze_count para registrar y limitar la cantidad de aplazamientos por notificación
ALTER TABLE notification ADD COLUMN IF NOT EXISTS snooze_count INT NOT NULL DEFAULT 0;
