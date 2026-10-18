package com.urleads.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.ImportConfirmResponse;
import com.urleads.dto.ImportPreviewResponse;
import com.urleads.dto.ImportRowPreview;
import com.urleads.dto.LeadResponse;
import com.urleads.entity.LeadImportReceipt;
import com.urleads.exception.LeadValidationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.LeadImportReceiptRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.service.ExcelImportParser;
import com.urleads.service.LeadService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadImportServiceImplTest {

    private static final String SECRET_KEY = "test-secret-salt-key-for-preview-tokens-2026";
    private static final ZoneId TEST_ZONE = ZoneId.of("America/Lima");
    private static final Instant BASE_INSTANT = Instant.parse("2026-10-18T10:00:00Z");

    private static final List<String> STANDARD_HEADERS = List.of(
            "nombre", "telefono", "correo", "service", "etapa", "priority", "origin", "proximoSeguimiento"
    );

    @Mock
    private LeadService leadService;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadImportReceiptRepository receiptRepository;

    private Clock clock;
    private ExcelImportParser parser;
    private LeadImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(BASE_INSTANT, TEST_ZONE);
        parser = new ExcelImportParser();
        importService = new LeadImportServiceImpl(
                parser,
                leadService,
                leadRepository,
                receiptRepository,
                clock,
                SECRET_KEY
        );
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
                            if (val instanceof String s) {
                                cell.setCellValue(s);
                            } else if (val instanceof Number n) {
                                cell.setCellValue(n.doubleValue());
                            } else if (val instanceof LocalDate ld) {
                                cell.setCellValue(ld.toString());
                            }
                        }
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("R1: Preview de archivo válido genera respuesta completa sin escrituras en base de datos")
    void testPreviewValidWorkbookProducesResultWithoutDbWrites() throws IOException {
        List<List<Object>> data = List.of(
                List.of("Juan Pérez", "987654321", "juan@example.com", "Web Design", "NUEVO", "MEDIA", "MANUAL", "2026-11-10"),
                List.of("María López", "912345678", "maria@example.com", "SEO", "EN_SEGUIMIENTO", "ALTA", "WEB", "2026-11-20")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "leads.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse response = importService.preview(1L, file);

        assertNotNull(response);
        assertNotNull(response.previewId());
        assertEquals(2, response.totalRows());
        assertEquals(2, response.validRows());
        assertEquals(0, response.invalidRows());
        assertTrue(response.canConfirm());
        assertEquals(2, response.rows().size());

        // Asegurar que preview no realiza escrituras
        verifyNoInteractions(receiptRepository);
        verify(leadService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("R3: Validación detallada campo por campo con diagnósticos específicos")
    void testPreviewRowValidationErrors() throws IOException {
        List<List<Object>> data = List.of(
                // Fila 2: Nombre muy corto, sin teléfono ni correo, etapa inválida, prioridad inválida, origen inválido, fecha inválida
                List.of("A", "", "", "Servicio".repeat(40), "ETAPA_RARA", "MAXIMA", "REDES", "15-11-2026"),
                // Fila 3: CERRADO limpia seguimiento
                List.of("Carlos Bueno", "987111222", "carlos@example.com", "CRM", "CERRADO", "MEDIA", "MANUAL", "2026-11-10")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "leads.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse response = importService.preview(1L, file);

        assertEquals(2, response.totalRows());
        assertEquals(1, response.validRows());
        assertEquals(1, response.invalidRows());
        assertFalse(response.canConfirm());

        ImportRowPreview row2 = response.rows().get(0);
        assertEquals(2, row2.rowNumber());
        assertEquals("INVALID", row2.status());
        assertTrue(row2.errors().contains("El nombre debe tener entre 2 y 120 caracteres"));
        assertTrue(row2.errors().contains("Debe registrar teléfono o correo"));
        assertTrue(row2.errors().contains("Etapa no válida: solo se permite NUEVO, EN_SEGUIMIENTO o CERRADO"));
        assertTrue(row2.errors().contains("Prioridad no válida: solo se permite BAJA, MEDIA o ALTA"));
        assertTrue(row2.errors().contains("Origen no válido: solo se permite WEB, REFERIDO o MANUAL"));
        assertTrue(row2.errors().contains("Fecha de próximo seguimiento no válida, use formato YYYY-MM-DD"));
        assertTrue(row2.errors().contains("El servicio no puede superar 255 caracteres"));

        ImportRowPreview row3 = response.rows().get(1);
        assertEquals(3, row3.rowNumber());
        assertEquals("VALID", row3.status());
        assertNull(row3.values().get("nextFollowUp")); // CERRADO limpia seguimiento
    }

    @Test
    @DisplayName("R4: Detecta duplicados normalizados intra-archivo de correo y teléfono")
    void testIntraFileDuplicatesDetected() throws IOException {
        List<List<Object>> data = List.of(
                List.of("Lead Original", "987654321", "dup@example.com", "Web", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"),
                List.of("Lead Dup Correo", "987000111", "  DUP@EXAMPLE.COM  ", "Web", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"),
                List.of("Lead Dup Telefono", "(987) 654-321", "otro@example.com", "Web", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "dups.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse response = importService.preview(1L, file);

        assertEquals(3, response.totalRows());
        assertEquals(1, response.validRows());
        assertEquals(2, response.invalidRows());
        assertFalse(response.canConfirm());

        ImportRowPreview row3 = response.rows().get(1);
        assertEquals("DUPLICATE", row3.status());
        assertTrue(row3.errors().stream().anyMatch(e -> e.contains("Correo duplicado en el archivo (fila 2)")));

        ImportRowPreview row4 = response.rows().get(2);
        assertEquals("DUPLICATE", row4.status());
        assertTrue(row4.errors().stream().anyMatch(e -> e.contains("Teléfono duplicado en el archivo (fila 2)")));
    }

    @Test
    @DisplayName("R4: Detecta duplicados contra la base de datos para el mismo propietario")
    void testDatabaseDuplicateSameOwnerDetected() throws IOException {
        org.mockito.Mockito.lenient().when(leadRepository.existsByUsuarioIdAndCorreoNorm(1L, "existente@example.com")).thenReturn(true);
        org.mockito.Mockito.lenient().when(leadRepository.existsByUsuarioIdAndTelefonoNorm(1L, "999888777")).thenReturn(true);

        List<List<Object>> data = List.of(
                List.of("Lead Existente 1", "911222333", "existente@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"),
                List.of("Lead Existente 2", "999-888-777", "nuevo@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "db_dups.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse response = importService.preview(1L, file);

        assertEquals(2, response.totalRows());
        assertEquals(0, response.validRows());
        assertEquals(2, response.invalidRows());
        assertFalse(response.canConfirm());

        assertTrue(response.rows().get(0).errors().contains("Ya existe un lead con este correo"));
        assertEquals("DUPLICATE", response.rows().get(0).status());
        assertTrue(response.rows().get(1).errors().contains("Ya existe un lead con este teléfono"));
        assertEquals("DUPLICATE", response.rows().get(1).status());
    }

    @Test
    @DisplayName("R4-owner: Contacto existente en OTRO propietario no genera falso duplicado (aislamiento estricto)")
    void testContactExistingInOtherOwnerDoesNotConflict() throws IOException {
        // Para usuario 1L retorna false (el contacto solo existe para usuario 2L)
        org.mockito.Mockito.lenient().when(leadRepository.existsByUsuarioIdAndCorreoNorm(1L, "contacto@example.com")).thenReturn(false);
        org.mockito.Mockito.lenient().when(leadRepository.existsByUsuarioIdAndTelefonoNorm(1L, "987654321")).thenReturn(false);

        List<List<Object>> data = List.of(
                List.of("Lead Propio", "987654321", "contacto@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "other_owner.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse response = importService.preview(1L, file);

        assertEquals(1, response.totalRows());
        assertEquals(1, response.validRows());
        assertEquals(0, response.invalidRows());
        assertTrue(response.canConfirm());
        assertEquals("VALID", response.rows().get(0).status());
    }

    @Test
    @DisplayName("R5: Confirmación exitosa crea leads con leadService, guarda recibo y es idempotente ante reintentos")
    void testSuccessfulConfirmAndReplayIdempotency() throws IOException {
        List<List<Object>> data = List.of(
                List.of("Lead Uno", "987654321", "uno@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"),
                List.of("Lead Dos", "987654322", "dos@example.com", "Serv", "EN_SEGUIMIENTO", "ALTA", "WEB", "2026-11-02")
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "confirm.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        // 1. Obtener vista previa
        ImportPreviewResponse previewResponse = importService.preview(1L, file);
        assertTrue(previewResponse.canConfirm());
        String previewId = previewResponse.previewId();

        // 2. Mockear creación de leads
        OffsetDateTime now = OffsetDateTime.now(clock);
        LeadResponse resp1 = new LeadResponse(101L, "Lead Uno", "987654321", "uno@example.com", "NUEVO", LocalDate.of(2026, 11, 1), now, now, "Serv", "MEDIA", "MANUAL", List.of());
        LeadResponse resp2 = new LeadResponse(102L, "Lead Dos", "987654322", "dos@example.com", "EN_SEGUIMIENTO", LocalDate.of(2026, 11, 2), now, now, "Serv", "ALTA", "WEB", List.of());

        when(receiptRepository.findByPreviewId(previewId)).thenReturn(Optional.empty());
        when(leadService.crear(eq(1L), any(CreateLeadRequest.class))).thenReturn(resp1, resp2);

        // 3. Confirmar primera vez
        ImportConfirmResponse confirmResponse = importService.confirm(1L, file, previewId);

        assertNotNull(confirmResponse);
        assertEquals(2, confirmResponse.importedCount());
        assertEquals(List.of(101L, 102L), confirmResponse.leadIds());
        assertEquals(previewId, confirmResponse.previewId());

        // Verificar inserciones
        verify(leadService, times(2)).crear(eq(1L), any(CreateLeadRequest.class));
        ArgumentCaptor<LeadImportReceipt> receiptCaptor = ArgumentCaptor.forClass(LeadImportReceipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());
        LeadImportReceipt savedReceipt = receiptCaptor.getValue();
        assertEquals(previewId, savedReceipt.getPreviewId());
        assertEquals(1L, savedReceipt.getUsuarioId());
        assertEquals("101,102", savedReceipt.getLeadIds());

        // 4. Reintentar confirmación idéntica (Replay)
        when(receiptRepository.findByPreviewId(previewId)).thenReturn(Optional.of(savedReceipt));

        ImportConfirmResponse replayResponse = importService.confirm(1L, file, previewId);

        assertEquals(confirmResponse.importedCount(), replayResponse.importedCount());
        assertEquals(confirmResponse.leadIds(), replayResponse.leadIds());
        assertEquals(confirmResponse.previewId(), replayResponse.previewId());

        // No se deben haber llamado más creaciones de leads
        verify(leadService, times(2)).crear(eq(1L), any(CreateLeadRequest.class));
    }

    @Test
    @DisplayName("R5: Confirmación falla si el archivo fue alterado (digest mismatch)")
    void testConfirmRejectsAlteredWorkbook() throws IOException {
        List<List<Object>> originalData = List.of(
                List.of("Lead Original", "987654321", "orig@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        );
        byte[] originalBytes = createWorkbookBytes(originalData);
        MockMultipartFile originalFile = new MockMultipartFile("file", "original.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", originalBytes);

        ImportPreviewResponse previewResponse = importService.preview(1L, originalFile);
        String previewId = previewResponse.previewId();

        // Archivo alterado
        List<List<Object>> alteredData = List.of(
                List.of("Lead Modificado", "987654321", "orig@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        );
        byte[] alteredBytes = createWorkbookBytes(alteredData);
        MockMultipartFile alteredFile = new MockMultipartFile("file", "original.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", alteredBytes);

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                importService.confirm(1L, alteredFile, previewId));

        assertTrue(ex.getMessage().contains("El archivo no coincide con la vista previa confirmada"));
        verifyNoInteractions(leadService);
    }

    @Test
    @DisplayName("R5: Token de vista previa expirado (>24h) es rechazado")
    void testConfirmRejectsExpiredToken() throws IOException {
        byte[] bytes = createWorkbookBytes(List.of(
                List.of("Lead Expira", "987654321", "exp@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "exp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse previewResponse = importService.preview(1L, file);
        String previewId = previewResponse.previewId();

        // Avanzar el reloj 24 horas y 1 minuto en el futuro
        Clock futureClock = Clock.fixed(BASE_INSTANT.plusSeconds(24 * 3600 + 60), TEST_ZONE);
        LeadImportServiceImpl futureImportService = new LeadImportServiceImpl(
                parser, leadService, leadRepository, receiptRepository, futureClock, SECRET_KEY
        );

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                futureImportService.confirm(1L, file, previewId));

        assertTrue(ex.getMessage().contains("ha expirado"));
        verifyNoInteractions(leadService);
    }

    @Test
    @DisplayName("R5: Token de otro propietario no puede ser confirmado ni revela existencia de recibo")
    void testConfirmRejectsOtherOwnerToken() throws IOException {
        byte[] bytes = createWorkbookBytes(List.of(
                List.of("Lead Privado", "987654321", "priv@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "priv.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        // Generado para usuario 1
        ImportPreviewResponse previewResponse = importService.preview(1L, file);
        String previewId = previewResponse.previewId();

        // Usuario 2 intenta confirmar
        assertThrows(ResourceNotFoundException.class, () ->
                importService.confirm(2L, file, previewId));

        verifyNoInteractions(leadService);
    }

    @Test
    @DisplayName("R5: Confirmación rechaza libro con filas que contienen errores")
    void testConfirmRejectsWorkbookWithErrors() throws IOException {
        List<List<Object>> data = List.of(
                List.of("Lead Valido", "987654321", "valido@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"),
                List.of("", "", "", "", "NUEVO", "MEDIA", "MANUAL", "2026-11-01") // Fila con error
        );
        byte[] bytes = createWorkbookBytes(data);
        MockMultipartFile file = new MockMultipartFile("file", "err.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse previewResponse = importService.preview(1L, file);
        String previewId = previewResponse.previewId();

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                importService.confirm(1L, file, previewId));

        assertTrue(ex.getMessage().contains("contiene filas con errores o duplicados"));
        verifyNoInteractions(leadService);
    }

    @Test
    @DisplayName("R5: Token alterado/corrupto es rechazado")
    void testConfirmRejectsTamperedToken() throws IOException {
        byte[] bytes = createWorkbookBytes(List.of(
                List.of("Lead", "987654321", "lead@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                importService.confirm(1L, file, "token-invalido-tampered"));

        assertEquals("Token de vista previa no válido", ex.getMessage());
        verifyNoInteractions(leadService);
    }

    @Test
    @DisplayName("Finding 1: Reintento de confirmación de un recibo ya existente está permitido tras 24h indefinidamente")
    void testReplayAllowedAfter24hForExistingReceipt() throws IOException {
        byte[] bytes = createWorkbookBytes(List.of(
                List.of("Lead Replay", "987654321", "replay@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "replay.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse previewResponse = importService.preview(1L, file);
        String previewId = previewResponse.previewId();

        // Calcular resumen para el recibo existente
        String fileHash;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            fileHash = java.util.HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LeadImportReceipt savedReceipt = new LeadImportReceipt(
                previewId,
                1L,
                fileHash,
                1,
                "500",
                OffsetDateTime.now(clock).minusDays(5) // Creado hace 5 días
        );
        when(receiptRepository.findByPreviewId(previewId)).thenReturn(Optional.of(savedReceipt));

        // Avanzar el reloj 48 horas en el futuro
        Clock futureClock = Clock.fixed(BASE_INSTANT.plusSeconds(48 * 3600), TEST_ZONE);
        LeadImportServiceImpl futureImportService = new LeadImportServiceImpl(
                parser, leadService, leadRepository, receiptRepository, futureClock, SECRET_KEY
        );

        ImportConfirmResponse replayResponse = futureImportService.confirm(1L, file, previewId);

        assertNotNull(replayResponse);
        assertEquals(1, replayResponse.importedCount());
        assertEquals(List.of(500L), replayResponse.leadIds());
        assertEquals(previewId, replayResponse.previewId());

        // Asegurar que no se llamaron inserciones adicionales
        verifyNoInteractions(leadService);
    }

    @Test
    @DisplayName("Finding 2: El token de vista previa es opaco y no revela datos en texto plano al decodificar base64")
    void testTokenIsOpaqueAndDoesNotLeakPlaintext() throws IOException {
        byte[] bytes = createWorkbookBytes(List.of(
                List.of("Lead Secreto", "987654321", "secreto@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "secreto.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewResponse previewResponse = importService.preview(12345L, file);
        String previewId = previewResponse.previewId();

        byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(previewId);
        String decodedText = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);

        // El token cifrado con AES-GCM no contiene el ID de usuario ni el texto de resumen plano
        assertFalse(decodedText.contains("12345:"), "El token no debe contener el ID de usuario en texto plano");
        assertFalse(decodedText.contains("secreto@example.com"), "El token no debe contener texto plano del archivo");
    }

    @Test
    @DisplayName("Finding 2: Secreto ausente o menor a 32 caracteres debe fallar cerrado")
    void testMissingOrWeakSecretFailsClosed() throws IOException {
        byte[] bytes = createWorkbookBytes(List.of(
                List.of("Lead", "987654321", "lead@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01")
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        LeadImportServiceImpl unconfiguredService = new LeadImportServiceImpl(
                parser, leadService, leadRepository, receiptRepository, clock, ""
        );
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                unconfiguredService.preview(1L, file));
        assertTrue(ex1.getMessage().contains("se requiere urleads.import.token-secret de al menos 32 caracteres"));

        LeadImportServiceImpl weakSecretService = new LeadImportServiceImpl(
                parser, leadService, leadRepository, receiptRepository, clock, "demasiado-corto"
        );
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                weakSecretService.preview(1L, file));
        assertTrue(ex2.getMessage().contains("se requiere urleads.import.token-secret de al menos 32 caracteres"));
    }
}
