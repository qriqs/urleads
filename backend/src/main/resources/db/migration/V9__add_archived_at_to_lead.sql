-- V9__add_archived_at_to_lead.sql
-- DSW-52 / C12: Archivo logico de leads mediante marca temporal archived_at

ALTER TABLE lead
ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE NULL;

CREATE INDEX idx_lead_usuario_archived_at ON lead (usuario_id, archived_at);
