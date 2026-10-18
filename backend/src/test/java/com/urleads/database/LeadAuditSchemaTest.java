package com.urleads.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("R1: Verificación de migración Flyway V7 para auditoría de leads")
class LeadAuditSchemaTest {

    @Test
    @DisplayName("R1: Migración V7 existe junto a las migraciones V1-V6 ya integradas")
    void flywayMigrationStructureVerified() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V*.sql");

        List<String> filenames = Arrays.stream(resources)
                .map(Resource::getFilename)
                .sorted()
                .toList();

        // Baseline migrations V1, V2, V3 must exist
        assertThat(filenames).anyMatch(f -> f.startsWith("V1__"));
        assertThat(filenames).anyMatch(f -> f.startsWith("V2__"));
        assertThat(filenames).anyMatch(f -> f.startsWith("V3__"));

        // Previously integrated C06-C08 migrations remain present.
        assertThat(filenames).anyMatch(f -> f.startsWith("V4__owner_normalized_contact_uniqueness"));
        assertThat(filenames).anyMatch(f -> f.startsWith("V5__add_priority_and_origin_to_lead"));
        assertThat(filenames).anyMatch(f -> f.startsWith("V6__create_tags_and_lead_tags"));

        // C09 migration V7 must exist
        assertThat(filenames).anyMatch(f -> f.startsWith("V7__create_lead_audit_log_table.sql"));

        // No migrations beyond V8 (C10 reserves V8; sibling C12 reserves V9)
        assertThat(filenames).noneMatch(f -> f.startsWith("V9__"));
    }

    @Test
    @DisplayName("R1: Contenido de V7 define la tabla lead_audit_log con FKs, restricciones e índices requeridos")
    void v7MigrationContentVerified() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V7__create_lead_audit_log_table.sql");

        assertThat(resources).hasSize(1);
        String sql = resources[0].getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        // Tabla dedicada e independiente
        assertThat(sql).contains("create table lead_audit_log");
        assertThat(sql).contains("id bigint generated always as identity primary key");
        assertThat(sql).contains("lead_id bigint not null");
        assertThat(sql).contains("usuario_id bigint not null");
        assertThat(sql).contains("field_name varchar(50) not null");
        assertThat(sql).contains("old_value text");
        assertThat(sql).contains("new_value text");
        assertThat(sql).contains("created_at timestamp with time zone not null default current_timestamp");

        // Integridad referencial
        assertThat(sql).contains("constraint fk_lead_audit_log_lead foreign key (lead_id) references lead (id) on delete cascade");
        assertThat(sql).contains("constraint fk_lead_audit_log_usuario foreign key (usuario_id) references usuario (id) on delete restrict");
        assertThat(sql).contains("constraint chk_lead_audit_log_field_not_blank check (field_name ~ '\\s')");

        // Índices de optimización para lectura paginada por propietario y orden temporal estable
        assertThat(sql).contains("create index idx_lead_audit_log_lead_user on lead_audit_log (lead_id, usuario_id, created_at desc, id desc)");
        assertThat(sql).contains("create index idx_lead_audit_log_created_at on lead_audit_log (created_at)");
    }
}
