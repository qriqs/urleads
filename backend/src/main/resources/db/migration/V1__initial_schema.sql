-- V1__initial_schema.sql
-- Esquema inicial para UrLeads CRM: usuario, lead y nota

CREATE TABLE usuario (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT uq_usuario_username UNIQUE (username),
    CONSTRAINT chk_usuario_username_not_blank CHECK (username ~ '\S'),
    CONSTRAINT chk_usuario_password_hash_not_blank CHECK (password_hash ~ '\S')
);

CREATE TABLE lead (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    telefono TEXT,
    correo TEXT,
    etapa VARCHAR(20) NOT NULL,
    proximo_seguimiento DATE,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lead_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT chk_lead_nombre CHECK (
        nombre ~ '\S'
        AND length(trim(nombre)) >= 2
        AND length(trim(nombre)) <= 120
    ),
    CONSTRAINT chk_lead_contacto CHECK (
        (telefono IS NOT NULL AND telefono ~ '\S')
        OR
        (correo IS NOT NULL AND correo ~ '\S')
    ),
    CONSTRAINT chk_lead_etapa CHECK (etapa IN ('NUEVO', 'EN_SEGUIMIENTO', 'CERRADO')),
    CONSTRAINT chk_lead_cerrado_sin_seguimiento CHECK (
        etapa != 'CERRADO' OR proximo_seguimiento IS NULL
    )
);

CREATE TABLE nota (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id BIGINT NOT NULL,
    contenido VARCHAR(2000) NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nota_lead FOREIGN KEY (lead_id) REFERENCES lead (id) ON DELETE CASCADE,
    CONSTRAINT chk_nota_contenido CHECK (
        contenido ~ '\S'
        AND length(contenido) <= 2000
    )
);

CREATE INDEX idx_lead_usuario_id ON lead (usuario_id);
CREATE INDEX idx_lead_etapa ON lead (etapa);
CREATE INDEX idx_lead_proximo_seguimiento ON lead (proximo_seguimiento);
CREATE INDEX idx_nota_lead_id_creado_en ON nota (lead_id, creado_en ASC);
