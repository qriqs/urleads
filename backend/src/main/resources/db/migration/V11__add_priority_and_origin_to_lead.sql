-- V11__add_priority_and_origin_to_lead.sql
-- Agregar campos de prioridad y origen con valores por defecto y restricciones (Backend V2 C07 / DSW-47)

ALTER TABLE lead
ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE lead
ADD CONSTRAINT chk_lead_priority CHECK (priority IN ('BAJA', 'MEDIA', 'ALTA')),
ADD CONSTRAINT chk_lead_origin CHECK (origin IN ('WEB', 'REFERIDO', 'MANUAL'));

CREATE INDEX idx_lead_priority ON lead (priority);
CREATE INDEX idx_lead_origin ON lead (origin);
