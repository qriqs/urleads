-- V12__create_tags_and_lead_tags.sql
-- Gestión de etiquetas (tags) y relaciones con leads por propietario (Backend V2 C08 / DSW-48)

CREATE TABLE tag (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tag_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT chk_tag_nombre CHECK (
        nombre ~ '\S'
        AND length(trim(nombre)) >= 1
        AND length(trim(nombre)) <= 100
    )
);

CREATE UNIQUE INDEX uq_tag_usuario_nombre_lower ON tag (usuario_id, lower(trim(nombre)));
CREATE INDEX idx_tag_usuario_id ON tag (usuario_id);

CREATE TABLE lead_tag (
    lead_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (lead_id, tag_id),
    CONSTRAINT fk_lead_tag_lead FOREIGN KEY (lead_id) REFERENCES lead (id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE INDEX idx_lead_tag_tag_id ON lead_tag (tag_id);

-- Restriccion a nivel de base de datos para impedir asociaciones cruzadas entre propietarios
CREATE OR REPLACE FUNCTION check_lead_tag_owner()
RETURNS TRIGGER AS $$
DECLARE
    v_lead_user BIGINT;
    v_tag_user BIGINT;
BEGIN
    SELECT usuario_id INTO v_lead_user FROM lead WHERE id = NEW.lead_id;
    SELECT usuario_id INTO v_tag_user FROM tag WHERE id = NEW.tag_id;
    IF v_lead_user IS NULL OR v_tag_user IS NULL OR v_lead_user <> v_tag_user THEN
        RAISE EXCEPTION 'Cross-owner tag association not allowed: lead owner % != tag owner %', v_lead_user, v_tag_user USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_lead_tag_owner
BEFORE INSERT OR UPDATE ON lead_tag
FOR EACH ROW EXECUTE FUNCTION check_lead_tag_owner();
