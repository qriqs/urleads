package com.urleads.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.urleads.database.TestDatabaseGuardInitializer;
import com.urleads.dto.ImportConfirmResponse;
import com.urleads.dto.ImportPreviewResponse;
import com.urleads.entity.Lead;
import com.urleads.entity.LeadImportReceipt;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadValidationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.LeadImportReceiptRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.LeadImportService;
import com.urleads.service.LeadService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Pruebas de integración sobre base de datos PostgreSQL real (DSW-50 / C10).
 * Protegidas por TestDatabaseGuardInitializer (exigen TEST_DB_* apuntando a loopback local y BD '_test').
 * Excluidas de Surefire estándar (**\/*IT.java) y activadas exclusivamente bajo perfil db-integration.
 */
@SpringBootTest(properties = "urleads.import.token-secret=guarded-integration-test-secret-at-least-32-chars-key")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional
@WithMockUser(username = "owner")
class LeadImportApiIT {

    private static final List<String> STANDARD_HEADERS = List.of(
            "nombre", "telefono", "correo", "service", "etapa", "priority", "origin", "proximoSeguimiento"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LeadImportService leadImportService;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private LeadService leadService;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LeadImportReceiptRepository receiptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    private Usuario owner;
    private Usuario other;

    @BeforeEach
    void setUp() {
        owner = crearUsuario("owner");
        other = crearUsuario("other");
        when(currentUserProvider.getCurrentUserId()).thenReturn(owner.getId());
    }

    private Usuario crearUsuario(String prefijo) {
        Usuario u = new Usuario();
        u.setUsername(prefijo + "_" + UUID.randomUUID());
        u.setPasswordHash("hash-sintetico");
        return usuarioRepository.saveAndFlush(u);
    }

    private byte[] createWorkbookBytes(List<List<Object>> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Leads");
            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < STANDARD_HEADERS.size(); i++) {
                headerRow.createCell(i).setCellValue(STANDARD_HEADERS.get(i));
            }
            if (rows != null) {
                for (int r = 0; r < rows.size(); r++) {
                    XSSFRow row = sheet.createRow(r + 1);
                    List<Object> values = rows.get(r);
                    for (int c = 0; c < values.size(); c++) {
                        Object val = values.get(c);
                        if (val != null) {
                            XSSFCell cell = row.createCell(c);
                            cell.setCellValue(val.toString());
                        }
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("IT-V8: La tabla lead_import_receipt existe con columnas, restricciones e índices definidos en V8")
    void testMigrationV8Structure() {
        Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'lead_import_receipt'",
                Integer.class
        );
        assertThat(tableExists).isNotNull().isGreaterThan(0);

        List<String> columns = jdbcTemplate.query(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'lead_import_receipt'",
                (rs, rowNum) -> rs.getString("column_name")
        );
        assertThat(columns).contains("id", "preview_id", "usuario_id", "file_hash", "row_count", "lead_ids", "confirmed_at");
    }

    @Test
    @DisplayName("IT-R5: Confirmación DB crea leads y recibo; replay idéntico retorna recibo sin duplicar leads ni outbox")
    void testConfirmAndReplayIdempotencyAgainstRealDatabase() throws IOException {
        String email1 = "lead1_" + UUID.randomUUID() + "@example.com";
        String email2 = "lead2_" + UUID.randomUUID() + "@example.com";

        List<List<Object>> data = List.of(
                List.of("Lead IT 1", "987654321", email1, "Cloud", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"),
                List.of("Lead IT 2", "987654322", email2, "AI", "EN_SEGUIMIENTO", "ALTA", "WEB", "2026-11-02")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "import_it.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        // 1. Preview
        ImportPreviewResponse previewResponse = leadImportService.preview(owner.getId(), file);
        assertThat(previewResponse.canConfirm()).isTrue();
        String previewId = previewResponse.previewId();

        int initialLeadCount = leadRepository.findByUsuarioId(owner.getId()).size();
        Integer initialOutboxCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE payload::jsonb ->> 'ownerId' = CAST(? AS text)", Integer.class, String.valueOf(owner.getId())
        );

        // 2. Primera confirmación
        ImportConfirmResponse confirmResponse = leadImportService.confirm(owner.getId(), file, previewId);
        assertNotNull(confirmResponse);
        assertEquals(2, confirmResponse.importedCount());

        List<Lead> leadsAfterConfirm = leadRepository.findByUsuarioId(owner.getId());
        assertEquals(initialLeadCount + 2, leadsAfterConfirm.size());

        LeadImportReceipt savedReceipt = receiptRepository.findByPreviewId(previewId).orElse(null);
        assertNotNull(savedReceipt);
        assertEquals(owner.getId(), savedReceipt.getUsuarioId());
        assertEquals(2, savedReceipt.getRowCount());

        Integer outboxCountAfterConfirm = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE payload::jsonb ->> 'ownerId' = CAST(? AS text)", Integer.class, String.valueOf(owner.getId())
        );
        assertEquals(initialOutboxCount + 2, outboxCountAfterConfirm);

        // 3. Exact Replay con mismo propietario y mismo archivo
        ImportConfirmResponse replayResponse = leadImportService.confirm(owner.getId(), file, previewId);
        assertEquals(confirmResponse.importedCount(), replayResponse.importedCount());
        assertEquals(confirmResponse.leadIds(), replayResponse.leadIds());

        // Asegurar que no se crearon leads ni outbox adicionales
        List<Lead> leadsAfterReplay = leadRepository.findByUsuarioId(owner.getId());
        assertEquals(leadsAfterConfirm.size(), leadsAfterReplay.size());

        Integer outboxCountAfterReplay = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE payload::jsonb ->> 'ownerId' = CAST(? AS text)", Integer.class, String.valueOf(owner.getId())
        );
        assertEquals(outboxCountAfterConfirm, outboxCountAfterReplay);
    }

    @Test
    @DisplayName("IT-R5: Confirmación de token ajeno retorna 404 y no altera estado en base de datos")
    void testConfirmCrossOwnerTokenFailsSafely() throws IOException {
        String email = "cross_" + UUID.randomUUID() + "@example.com";
        byte[] bytes = createWorkbookBytes(List.of(
                List.of("Lead Cross", "987654321", email, "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "cross.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        // Preview para owner
        ImportPreviewResponse previewResponse = leadImportService.preview(owner.getId(), file);
        String previewId = previewResponse.previewId();

        // Intento de confirmación por other usuario
        assertThrows(ResourceNotFoundException.class, () ->
                leadImportService.confirm(other.getId(), file, previewId));

        assertThat(leadRepository.findByUsuarioId(other.getId())).isEmpty();
    }

    @Test
    @DisplayName("IT-R5: Falla tardía en fila durante inserción provoca rollback atómico completo (0 leads, 0 recibos, 0 outbox del lote)")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testBatchFailureRollsBackAllCreatedLeads() throws IOException {
        String email1 = "valid1_" + UUID.randomUUID() + "@example.com";
        String email2 = "valid2_" + UUID.randomUUID() + "@example.com";

        int leadCountBefore = leadRepository.findByUsuarioId(owner.getId()).size();
        Integer outboxCountBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE payload::jsonb ->> 'ownerId' = CAST(? AS text)", Integer.class, owner.getId()
        );

        // Libro con 2 filas válidas (prevalidación pasa completamente para ambas)
        List<List<Object>> data = List.of(
                List.of("Fila 1 Valida", "987654321", email1, "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"),
                List.of("Fila 2 Con Falla Tardía", "987654322", email2, "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-02")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "batch_rollback.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse previewResponse = leadImportService.preview(owner.getId(), file);
        String previewId = previewResponse.previewId();
        assertThat(previewResponse.canConfirm()).isTrue();

        // Inyección de falla tardía: la fila 1 ejecuta el método real y persiste en BD / outbox;
        // la fila 2 lanza excepción de persistencia simulada provocando rollback de la transacción de confirm()
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        org.mockito.Mockito.doAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                return invocation.callRealMethod();
            }
            throw new org.springframework.dao.DataIntegrityViolationException("Falla simulada de persistencia tardía en fila 2");
        }).when(leadService).crear(org.mockito.ArgumentMatchers.eq(owner.getId()), org.mockito.ArgumentMatchers.any(com.urleads.dto.CreateLeadRequest.class));

        // Confirm debe fallar con la excepción tardía inyectada
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                leadImportService.confirm(owner.getId(), file, previewId));

        // Demostrar que la primera fila ejecutó el método real antes de la falla
        assertEquals(2, callCount.get());

        // Verificar rollback atómico total en base de datos: cero leads agregados por el lote
        int leadCountAfter = leadRepository.findByUsuarioId(owner.getId()).size();
        assertEquals(leadCountBefore, leadCountAfter);

        // Cero eventos outbox agregados por el lote
        Integer outboxCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE payload::jsonb ->> 'ownerId' = CAST(? AS text)", Integer.class, owner.getId()
        );
        assertEquals(outboxCountBefore, outboxCountAfter);

        // Cero recibos confirmados
        assertThat(receiptRepository.findByPreviewId(previewId)).isEmpty();
    }
}
