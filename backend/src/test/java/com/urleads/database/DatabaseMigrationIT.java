package com.urleads.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integracion para la migracion inicial de Flyway y restricciones
 * del esquema en PostgreSQL.
 *
 * Se ejecuta bajo el perfil opt-in de Maven Failsafe:
 *   ./mvnw verify -Pdb-integration
 *
 * Requiere una instancia aislada de PostgreSQL configurada mediante variables
 * de entorno obligatorias (TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD).
 * El inicializador {@link TestDatabaseGuardInitializer} valida de forma fail-fast
 * que la conexion sea local y el nombre de la base de datos termine en '_test'.
 */
@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
class DatabaseMigrationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("La migracion inicial creo el historial de Flyway y la version 6 fue aplicada")
    void initialMigrationAppliedSuccessfully() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(flyway.info().current().getState().isApplied()).isTrue();

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class
        );
        assertThat(historyCount).isNotNull().isEqualTo(1);
    }

    @Test
    @DisplayName("Una segunda ejecucion de migrate() no aplica cambios adicionales (idempotencia)")
    void secondFlywayMigrateAppliesNothing() {
        MigrateResult result = flyway.migrate();
        assertThat(result.migrationsExecuted).isZero();
    }

    @Test
    @DisplayName("Las tablas requeridas y sus columnas existen en la base de datos")
    void expectedTablesAndColumnsExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        );
        assertThat(tables).contains("flyway_schema_history", "usuario", "lead", "nota", "outbox", "tag", "lead_tag");

        List<String> usuarioColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'usuario'",
                String.class
        );
        assertThat(usuarioColumns).contains("id", "username", "password_hash");

        List<String> leadColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'lead'",
                String.class
        );
        assertThat(leadColumns).contains(
                "id", "usuario_id", "nombre", "telefono", "correo",
                "etapa", "proximo_seguimiento", "creado_en", "actualizado_en", "service",
                "priority", "origin", "telefono_norm", "correo_norm"
        );

        List<String> tagColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'tag'",
                String.class);
        assertThat(tagColumns).contains("id", "usuario_id", "nombre", "creado_en");
        List<String> leadTagColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'lead_tag'",
                String.class);
        assertThat(leadTagColumns).contains("lead_id", "tag_id");

        List<String> notaColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'nota'",
                String.class
        );
        assertThat(notaColumns).contains("id", "lead_id", "contenido", "creado_en");

        List<String> outboxColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'outbox'",
                String.class
        );
        assertThat(outboxColumns).contains(
                "id", "event_id", "event_type", "routing_key", "exchange",
                "payload", "status", "retry_count", "last_error",
                "created_at", "sent_at", "next_retry_at"
        );
    }

    @Test
    @Transactional
    @DisplayName("usuario: no permite nombres de usuario duplicados (UK)")
    void usuarioEnforcesUniqueUsername() {
        String username = "synthetic_user_" + UUID.randomUUID();
        createTestUser(username);

        assertThatThrownBy(() -> createTestUser(username))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("usuario: no permite username vacio")
    void usuarioRejectsEmptyUsername() {
        assertThatThrownBy(() -> createTestUser(""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("usuario: no permite username con solo espacios en blanco")
    void usuarioRejectsWhitespaceUsername() {
        assertThatThrownBy(() -> createTestUser("   "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("usuario: no permite username compuesto solo por tabuladores y saltos de linea")
    void usuarioRejectsTabAndNewlineOnlyUsername() {
        assertThatThrownBy(() -> createTestUser("\t\n\r"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("usuario: no permite password_hash vacio o con solo espacios en blanco")
    void usuarioRejectsBlankPasswordHash() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO usuario (username, password_hash) VALUES (?, ?)",
                "synthetic_user_" + UUID.randomUUID(),
                "   "
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("usuario: no permite password_hash con solo tabuladores y saltos de linea")
    void usuarioRejectsTabAndNewlineOnlyPasswordHash() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO usuario (username, password_hash) VALUES (?, ?)",
                "synthetic_user_" + UUID.randomUUID(),
                "\t\n\r"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza usuario_id inexistente (FK)")
    void leadRejectsNonExistentUsuarioId() {
        assertThatThrownBy(() -> createTestLead(
                -999999L,
                "Contacto Prueba",
                "+51900000000",
                null,
                "NUEVO",
                null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza nombre menor a 2 caracteres")
    void leadRejectsNameShorterThanTwoChars() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "A", "+51900000000", null, "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza nombre compuesto solo por espacios en blanco")
    void leadRejectsBlankName() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "   ", "+51900000000", null, "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza nombre compuesto solo por tabuladores y saltos de linea")
    void leadRejectsTabAndNewlineOnlyName() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "\t\n\r", "+51900000000", null, "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza nombre mayor a 120 caracteres")
    void leadRejectsNameLongerThan120Chars() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        String overlyLongName = "A".repeat(121);
        assertThatThrownBy(() -> createTestLead(usuarioId, overlyLongName, "+51900000000", null, "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: permite nombres validos en los limites permitidos (2 y 120 caracteres)")
    void leadAllowsValidNames() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());

        Long leadIdMin = createTestLead(usuarioId, "AB", "+51900000000", null, "NUEVO", null);
        assertThat(leadIdMin).isNotNull();

        String valid120Name = "A".repeat(120);
        Long leadIdMax = createTestLead(usuarioId, valid120Name, "+51900000000", null, "NUEVO", null);
        assertThat(leadIdMax).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza registros sin medios de contacto (telefono y correo nulos)")
    void leadRejectsBothContactsNull() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "Contacto", null, null, "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza registros con medios de contacto en blanco (espacios)")
    void leadRejectsBothContactsBlank() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "Contacto", "   ", "   ", "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza medios de contacto compuestos unicamente por tabuladores o saltos de linea")
    void leadRejectsTabAndNewlineOnlyContacts() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "Contacto", "\t\t", "\n\r", "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: permite registrar lead solo con telefono")
    void leadAllowsPhoneOnly() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadPhone = createTestLead(usuarioId, "Contacto Tel", "+51999999999", null, "NUEVO", null);
        assertThat(leadPhone).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("lead: permite registrar lead solo con correo")
    void leadAllowsEmailOnly() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadEmail = createTestLead(usuarioId, "Contacto Correo", null, "contacto@example.test", "NUEVO", null);
        assertThat(leadEmail).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("lead: permite registrar lead con telefono y correo a la vez")
    void leadAllowsBothPhoneAndEmail() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadBoth = createTestLead(usuarioId, "Contacto Ambos", "+51999999999", "contacto@example.test", "NUEVO", null);
        assertThat(leadBoth).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("lead: almacenamiento TEXT sin limites arbitrarios de 50 caracteres para telefono ni 255 para correo")
    void leadAllowsLongContactsWithoutArbitraryCaps() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        String longPhone = "+51" + "9".repeat(80); // 83 chars (> 50)
        String longEmail = "local." + "a".repeat(250) + "@domain.example.test"; // > 255 chars

        Long leadId = createTestLead(usuarioId, "Contacto Largo", longPhone, longEmail, "NUEVO", null);
        assertThat(leadId).isNotNull();

        String storedPhone = jdbcTemplate.queryForObject("SELECT telefono FROM lead WHERE id = ?", String.class, leadId);
        String storedEmail = jdbcTemplate.queryForObject("SELECT correo FROM lead WHERE id = ?", String.class, leadId);

        assertThat(storedPhone).isEqualTo(longPhone);
        assertThat(storedEmail).isEqualTo(longEmail);
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza etapas no permitidas")
    void leadRejectsInvalidStage() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "Contacto", "+51900000000", null, "DESCARTADO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: permite las etapas validas NUEVO, EN_SEGUIMIENTO y CERRADO")
    void leadAllowsValidStages() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());

        Long idNuevo = createTestLead(usuarioId, "Lead 1", "+51900000000", null, "NUEVO", null);
        Long idSeguimiento = createTestLead(usuarioId, "Lead 2", "+51900000000", null, "EN_SEGUIMIENTO", LocalDate.now());
        Long idCerrado = createTestLead(usuarioId, "Lead 3", "+51900000000", null, "CERRADO", null);

        assertThat(idNuevo).isNotNull();
        assertThat(idSeguimiento).isNotNull();
        assertThat(idCerrado).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza insertar un lead CERRADO con fecha de proximo_seguimiento")
    void leadClosedRejectsFollowUpDate() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> createTestLead(usuarioId, "Lead Cerrado", "+51900000000", null, "CERRADO", LocalDate.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: permite insertar un lead CERRADO sin fecha de proximo_seguimiento")
    void leadClosedAllowsNullFollowUpDate() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long idCerrado = createTestLead(usuarioId, "Lead Cerrado Valido", "+51900000000", null, "CERRADO", null);
        assertThat(idCerrado).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("lead: rechaza transicionar a CERRADO si no se limpia proximo_seguimiento")
    void leadUpdateToClosedRejectsRetainedFollowUp() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Activo", "+51900000000", null, "EN_SEGUIMIENTO", LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE lead SET etapa = 'CERRADO' WHERE id = ?",
                leadId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("lead: permite transicionar a CERRADO limpiando proximo_seguimiento")
    void leadUpdateToClosedAllowsClearedFollowUp() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Activo", "+51900000000", null, "EN_SEGUIMIENTO", LocalDate.now().plusDays(3));

        int updated = jdbcTemplate.update(
                "UPDATE lead SET etapa = 'CERRADO', proximo_seguimiento = NULL WHERE id = ?",
                leadId
        );
        assertThat(updated).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("nota: rechaza lead_id inexistente (FK)")
    void notaRejectsNonExistentLeadId() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO nota (lead_id, contenido) VALUES (?, ?)",
                -999999L, "Nota de prueba"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("nota: rechaza contenido compuesto solo por espacios en blanco")
    void notaRejectsBlankContent() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Con Notas", "+51900000000", null, "NUEVO", null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO nota (lead_id, contenido) VALUES (?, ?)",
                leadId, "   "
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("nota: rechaza contenido compuesto solo por tabuladores y saltos de linea")
    void notaRejectsTabAndNewlineOnlyContent() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Con Notas", "+51900000000", null, "NUEVO", null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO nota (lead_id, contenido) VALUES (?, ?)",
                leadId, "\t\t\n\r"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("nota: rechaza contenido que excede los 2000 caracteres")
    void notaRejectsContentExceeding2000Chars() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Con Notas", "+51900000000", null, "NUEVO", null);

        String overlyLongContent = "A".repeat(2001);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO nota (lead_id, contenido) VALUES (?, ?)",
                leadId, overlyLongContent
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("nota: limites de contenido - permite nota de exactamente 1 caracter")
    void notaAllowsMinimumBoundaryContent() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Con Notas", "+51900000000", null, "NUEVO", null);

        int inserted = jdbcTemplate.update(
                "INSERT INTO nota (lead_id, contenido) VALUES (?, ?)",
                leadId, "X"
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("nota: limites de contenido - permite nota de exactamente 2000 caracteres")
    void notaAllowsMaximumBoundaryContent() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Con Notas", "+51900000000", null, "NUEVO", null);

        String maxContent = "B".repeat(2000);
        int inserted = jdbcTemplate.update(
                "INSERT INTO nota (lead_id, contenido) VALUES (?, ?)",
                leadId, maxContent
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("nota: eliminacion en cascada al eliminar el lead")
    void cascadingDeletionRemovesNotesWhenLeadIsDeleted() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Para Borrar", "+51900000000", null, "NUEVO", null);

        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", leadId, "Nota 1");
        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", leadId, "Nota 2");

        Integer noteCountBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM nota WHERE lead_id = ?",
                Integer.class,
                leadId
        );
        assertThat(noteCountBefore).isEqualTo(2);

        // Eliminar lead
        int deletedLeads = jdbcTemplate.update("DELETE FROM lead WHERE id = ?", leadId);
        assertThat(deletedLeads).isEqualTo(1);

        // Verificar que las notas asociadas fueron eliminadas en cascada
        Integer noteCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM nota WHERE lead_id = ?",
                Integer.class,
                leadId
        );
        assertThat(noteCountAfter).isZero();

        // El usuario debe continuar existiendo
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario WHERE id = ?",
                Integer.class,
                usuarioId
        );
        assertThat(userCount).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("C07: lead rechaza prioridad inválida (CHECK chk_lead_priority)")
    void leadRejectsInvalidPriority() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO lead (usuario_id, nombre, telefono, etapa, priority, origin) VALUES (?, ?, ?, ?, ?, ?)",
                usuarioId, "Contacto", "+51900000000", "NUEVO", "URGENTE", "MANUAL"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("C07: lead rechaza origen inválido (CHECK chk_lead_origin)")
    void leadRejectsInvalidOrigin() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO lead (usuario_id, nombre, telefono, etapa, priority, origin) VALUES (?, ?, ?, ?, ?, ?)",
                usuarioId, "Contacto", "+51900000000", "NUEVO", "MEDIA", "PARTNER"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("C07: lead permite valores válidos de catálogo para priority y origin")
    void leadAllowsValidPrioritiesAndOrigins() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());

        Long id1 = jdbcTemplate.queryForObject(
                "INSERT INTO lead (usuario_id, nombre, telefono, etapa, priority, origin) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, usuarioId, "Lead 1", "+51900000001", "NUEVO", "BAJA", "WEB"
        );
        Long id2 = jdbcTemplate.queryForObject(
                "INSERT INTO lead (usuario_id, nombre, telefono, etapa, priority, origin) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, usuarioId, "Lead 2", "+51900000002", "NUEVO", "MEDIA", "REFERIDO"
        );
        Long id3 = jdbcTemplate.queryForObject(
                "INSERT INTO lead (usuario_id, nombre, telefono, etapa, priority, origin) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, usuarioId, "Lead 3", "+51900000003", "NUEVO", "ALTA", "MANUAL"
        );

        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id3).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("C07: inserción que omite priority y origin recibe valores por defecto MEDIA y MANUAL en la BD")
    void leadDefaultsPriorityAndOriginOnRawInsert() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long leadId = createTestLead(usuarioId, "Lead Legacy", "+51900000000", null, "NUEVO", null);

        String priority = jdbcTemplate.queryForObject("SELECT priority FROM lead WHERE id = ?", String.class, leadId);
        String origin = jdbcTemplate.queryForObject("SELECT origin FROM lead WHERE id = ?", String.class, leadId);

        assertThat(priority).isEqualTo("MEDIA");
        assertThat(origin).isEqualTo("MANUAL");
    }

    // C06 V4 normalized-contact migration regressions

    @Test
    @Transactional
    @DisplayName("lead V4: columnas generadas normalizan teléfono y correo preservando valores crudos")
    void leadNormalizationColumnsComputeCorrectly() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        String rawPhone = "+1 (212) 555-0100";
        String rawEmail = "\t\n  Test.User@Example.COM  \r\n";
        Long leadId = createTestLead(usuarioId, "Lead Normalizado", rawPhone, rawEmail, "NUEVO", null);
        assertThat(jdbcTemplate.queryForObject("SELECT telefono FROM lead WHERE id = ?", String.class, leadId)).isEqualTo(rawPhone);
        assertThat(jdbcTemplate.queryForObject("SELECT correo FROM lead WHERE id = ?", String.class, leadId)).isEqualTo(rawEmail);
        assertThat(jdbcTemplate.queryForObject("SELECT telefono_norm FROM lead WHERE id = ?", String.class, leadId)).isEqualTo("12125550100");
        assertThat(jdbcTemplate.queryForObject("SELECT correo_norm FROM lead WHERE id = ?", String.class, leadId)).isEqualTo("test.user@example.com");
    }

    @Test
    @Transactional
    void leadUniqueConstraintRejectsDuplicateNormalizedEmailForSameOwner() {
        Long owner = createTestUser("synthetic_user_" + UUID.randomUUID());
        createTestLead(owner, "First", null, "ana@example.com", "NUEVO", null);
        assertThatThrownBy(() -> createTestLead(owner, "Second", null, " ANA@EXAMPLE.COM ", "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void leadUniqueConstraintRejectsDuplicateNormalizedPhoneForSameOwner() {
        Long owner = createTestUser("synthetic_user_" + UUID.randomUUID());
        createTestLead(owner, "First", "+51 (999) 111-222", null, "NUEVO", null);
        assertThatThrownBy(() -> createTestLead(owner, "Second", "51.999.111.222", null, "NUEVO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void leadAllowsSameContactsForDifferentOwners() {
        Long ownerA = createTestUser("synthetic_user_a_" + UUID.randomUUID());
        Long ownerB = createTestUser("synthetic_user_b_" + UUID.randomUUID());
        assertThat(createTestLead(ownerA, "A", "+51999111222", "same@example.com", "NUEVO", null)).isNotNull();
        assertThat(createTestLead(ownerB, "B", "51 999 111 222", "SAME@example.com", "NUEVO", null)).isNotNull();
    }

    @Test
    @Transactional
    void leadAllowsMultipleMissingOrNonNumericContactValues() {
        Long owner = createTestUser("synthetic_user_" + UUID.randomUUID());
        assertThat(createTestLead(owner, "A", "---", "a@example.com", "NUEVO", null)).isNotNull();
        assertThat(createTestLead(owner, "B", "+ - ()", "b@example.com", "NUEVO", null)).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("tag: no permite nombres duplicados para el mismo propietario case-insensitively")
    void tagEnforcesUniqueNamePerOwnerCaseInsensitive() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());
        createTestTag(usuarioId, "Prospecto");

        assertThatThrownBy(() -> createTestTag(usuarioId, "  prospecto  "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    @Transactional
    @DisplayName("tag: permite el mismo nombre en propietarios distintos")
    void tagAllowsSameNameAcrossDifferentOwners() {
        Long usuarioA = createTestUser("synthetic_user_" + UUID.randomUUID());
        Long usuarioB = createTestUser("synthetic_user_" + UUID.randomUUID());

        Long tagA = createTestTag(usuarioA, "Prospecto");
        Long tagB = createTestTag(usuarioB, "Prospecto");

        assertThat(tagA).isNotNull();
        assertThat(tagB).isNotNull();
        assertThat(tagA).isNotEqualTo(tagB);
    }
    @Test
    @Transactional
    @DisplayName("tag: rechaza nombre en blanco o solo espacios")
    void tagRejectsBlankName() {
        Long usuarioId = createTestUser("synthetic_user_" + UUID.randomUUID());

        assertThatThrownBy(() -> createTestTag(usuarioId, "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    @Transactional
    @DisplayName("lead_tag: disparador trg_check_lead_tag_owner impide asociacion entre propietarios distintos")
    void leadTagTriggerPreventsCrossOwnerAssociation() {
        Long usuarioA = createTestUser("user_a_" + UUID.randomUUID());
        Long usuarioB = createTestUser("user_b_" + UUID.randomUUID());

        Long leadA = createTestLead(usuarioA, "Lead Usuario A", "999", null, "NUEVO", null);
        Long tagB = createTestTag(usuarioB, "Tag Usuario B");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)",
                leadA, tagB
        )).isInstanceOf(org.springframework.dao.DataAccessException.class)
          .hasMessageContaining("Cross-owner tag association not allowed");
    }
    @Test
    @Transactional
    @DisplayName("lead_tag: permite asociacion valida entre lead y tag del mismo propietario")
    void leadTagAllowsSameOwnerAssociation() {
        Long usuario = createTestUser("user_same_" + UUID.randomUUID());
        Long lead = createTestLead(usuario, "Lead Valido", "999", null, "NUEVO", null);
        Long tag = createTestTag(usuario, "Tag Valido");

        int inserted = jdbcTemplate.update(
                "INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)",
                lead, tag
        );
        assertThat(inserted).isEqualTo(1);
    }
    @Test
    @Transactional
    @DisplayName("lead_tag: eliminacion en cascada de lead elimina sus notas y enlaces lead_tag pero conserva las etiquetas")
    void deletingLeadDeletesNotesAndLeadTagRowsPreservingTags() {
        Long usuario = createTestUser("user_del_lead_" + UUID.randomUUID());
        Long lead = createTestLead(usuario, "Lead Completo", "999", null, "NUEVO", null);
        Long tag = createTestTag(usuario, "Etiqueta Conservada");

        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", lead, "Nota de prueba");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", lead, tag);

        // Eliminar lead
        int deleted = jdbcTemplate.update("DELETE FROM lead WHERE id = ?", lead);
        assertThat(deleted).isEqualTo(1);

        // Notas y filas en lead_tag deben haberse limpiado
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM nota WHERE lead_id = ?", Integer.class, lead)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead)).isZero();

        // La etiqueta original debe permanecer intacta
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tag WHERE id = ?", Integer.class, tag)).isEqualTo(1);
    }
    @Test
    @Transactional
    @DisplayName("lead_tag: eliminacion de etiqueta elimina enlaces lead_tag pero conserva el lead y sus notas")
    void deletingTagDeletesLeadTagRowsPreservingLeadAndNotes() {
        Long usuario = createTestUser("user_del_tag_" + UUID.randomUUID());
        Long lead = createTestLead(usuario, "Lead Conservado", "999", null, "NUEVO", null);
        Long tag = createTestTag(usuario, "Etiqueta a Borrar");

        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", lead, "Nota importante");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", lead, tag);

        // Eliminar etiqueta
        int deleted = jdbcTemplate.update("DELETE FROM tag WHERE id = ?", tag);
        assertThat(deleted).isEqualTo(1);

        // El enlace lead_tag se eliminó en cascada
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead_tag WHERE tag_id = ?", Integer.class, tag)).isZero();

        // El lead y su nota continuan existiendo
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead WHERE id = ?", Integer.class, lead)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM nota WHERE lead_id = ?", Integer.class, lead)).isEqualTo(1);
    }

    private Long createTestTag(Long usuarioId, String nombre) {
        return jdbcTemplate.queryForObject("INSERT INTO tag (usuario_id, nombre) VALUES (?, ?) RETURNING id", Long.class, usuarioId, nombre);
    }

    private Long createTestUser(String username) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO usuario (username, password_hash) VALUES (?, ?) RETURNING id",
                Long.class,
                username,
                "$2a$10$e8w.x.N9c7o6j5q4z3y2x1w0v9u8t7s6r5q4p3o2n1m0l9k8j7h6"
        );
    }

    private Long createTestLead(Long usuarioId, String nombre, String telefono, String correo, String etapa, LocalDate proximoSeguimiento) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO lead (usuario_id, nombre, telefono, correo, etapa, proximo_seguimiento) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                usuarioId,
                nombre,
                telefono,
                correo,
                etapa,
                proximoSeguimiento != null ? java.sql.Date.valueOf(proximoSeguimiento) : null
        );
    }

}
