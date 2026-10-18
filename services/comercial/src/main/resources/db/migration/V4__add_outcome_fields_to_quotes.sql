-- V4__add_outcome_fields_to_quotes.sql
-- Registro estructurado de motivo y fecha de resultado comercial (S08)

ALTER TABLE quotes ADD COLUMN IF NOT EXISTS outcome_reason VARCHAR(50);
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS outcome_notes TEXT;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS outcome_date TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_quotes_outcome_reason ON quotes(outcome_reason);
CREATE INDEX IF NOT EXISTS idx_quotes_outcome_date ON quotes(outcome_date);
