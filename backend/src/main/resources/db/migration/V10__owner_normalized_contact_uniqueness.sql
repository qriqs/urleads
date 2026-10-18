-- V10__owner_normalized_contact_uniqueness.sql
-- DSW-46 / C06: Columnas generadas normalizadas y restricciones de unicidad por propietario

ALTER TABLE lead
ADD COLUMN telefono_norm TEXT GENERATED ALWAYS AS (NULLIF(regexp_replace(telefono, '[^0-9]', '', 'g'), '')) STORED,
ADD COLUMN correo_norm TEXT GENERATED ALWAYS AS (NULLIF(lower(btrim(correo, E' \t\n\r\013\f\x01\x02\x03\x04\x05\x06\x07\x08\x0e\x0f\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f')), '')) STORED;

ALTER TABLE lead
ADD CONSTRAINT uq_lead_owner_telefono_norm UNIQUE (usuario_id, telefono_norm),
ADD CONSTRAINT uq_lead_owner_correo_norm UNIQUE (usuario_id, correo_norm);
