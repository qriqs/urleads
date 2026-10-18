package com.urleads.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.urleads.exception.LeadValidationException;
import com.urleads.service.ExcelImportParser.ParsedRow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelImportParserTest {

    private ExcelImportParser parser;

    private static final List<String> STANDARD_HEADERS = List.of(
            "nombre", "telefono", "correo", "service", "etapa", "priority", "origin", "proximoSeguimiento"
    );

    @BeforeEach
    void setUp() {
        parser = new ExcelImportParser();
    }

    private byte[] createWorkbookBytes(List<String> headers, List<List<Object>> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Leads");
            if (headers != null) {
                XSSFRow headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    headerRow.createCell(i).setCellValue(headers.get(i));
                }
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
                            } else if (val instanceof Boolean b) {
                                cell.setCellValue(b);
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
    @DisplayName("R1: Debe parsear correctamente un libro válido con cabeceras estándar")
    void testParseValidWorkbook() throws IOException {
        List<List<Object>> data = List.of(
                List.of("Carlos Mendoza", "987654321", "carlos@example.com", "Consultoría", "NUEVO", "ALTA", "WEB", "2026-11-15"),
                List.of("Ana Gómez", "912345678", "ana@example.com", "Auditoría", "EN_SEGUIMIENTO", "MEDIA", "REFERIDO", "2026-12-01")
        );
        byte[] bytes = createWorkbookBytes(STANDARD_HEADERS, data);

        List<ParsedRow> rows = parser.parse(bytes, "leads.xlsx");

        assertEquals(2, rows.size());
        ParsedRow r1 = rows.get(0);
        assertEquals(2, r1.rowNumber()); // Fila 2 en Excel
        assertEquals("Carlos Mendoza", r1.nombre());
        assertEquals("987654321", r1.telefono());
        assertEquals("carlos@example.com", r1.correo());
        assertEquals("Consultoría", r1.service());
        assertEquals("NUEVO", r1.etapa());
        assertEquals("ALTA", r1.priority());
        assertEquals("WEB", r1.origin());
        assertEquals("2026-11-15", r1.proximoSeguimiento());
        assertTrue(r1.formulasByField().isEmpty());
        assertFalse(r1.hasExtraColumns());

        ParsedRow r2 = rows.get(1);
        assertEquals(3, r2.rowNumber()); // Fila 3 en Excel
        assertEquals("Ana Gómez", r2.nombre());
        assertEquals("912345678", r2.telefono());
    }

    @Test
    @DisplayName("R3: Debe ignorar filas totalmente en blanco sin afectar conteo ni numeración")
    void testBlankRowsSafelyIgnored() throws IOException {
        List<List<Object>> data = new ArrayList<>();
        data.add(List.of("Lead Uno", "987654321", "uno@example.com", "Web", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"));
        data.add(List.of("", "", "", "", "", "", "", "")); // Fila 3 en blanco
        data.add(List.of("Lead Tres", "987654323", "tres@example.com", "Cloud", "NUEVO", "MEDIA", "MANUAL", "2026-11-03"));

        byte[] bytes = createWorkbookBytes(STANDARD_HEADERS, data);
        List<ParsedRow> rows = parser.parse(bytes, "test.xlsx");

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals("Lead Uno", rows.get(0).nombre());
        assertEquals(4, rows.get(1).rowNumber());
        assertEquals("Lead Tres", rows.get(1).nombre());
    }

    @Test
    @DisplayName("R3: Fila parcialmente poblada no se ignora y se entrega con valores nulos")
    void testPartiallyPopulatedRowNotIgnored() throws IOException {
        List<List<Object>> data = List.of(
                List.of("Lead Incompleto", "", "", "", "NUEVO", "", "", "")
        );
        byte[] bytes = createWorkbookBytes(STANDARD_HEADERS, data);
        List<ParsedRow> rows = parser.parse(bytes, "test.xlsx");

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals("Lead Incompleto", rows.get(0).nombre());
        assertNull(rows.get(0).telefono());
        assertNull(rows.get(0).correo());
    }

    @Test
    @DisplayName("R1: Debe rechazar cabeceras con nombres diferentes")
    void testRejectInvalidHeaderNames() throws IOException {
        List<String> badHeaders = List.of(
                "name", "telefono", "correo", "service", "etapa", "priority", "origin", "proximoSeguimiento"
        );
        byte[] bytes = createWorkbookBytes(badHeaders, List.of(List.of("A", "B", "C", "D", "E", "F", "G", "H")));

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(bytes, "test.xlsx"));
        assertTrue(ex.getMessage().contains("Cabeceras de plantilla inválidas"));
    }

    @Test
    @DisplayName("R1: Debe rechazar cabeceras en orden incorrecto")
    void testRejectSwappedHeaders() throws IOException {
        List<String> swappedHeaders = List.of(
                "telefono", "nombre", "correo", "service", "etapa", "priority", "origin", "proximoSeguimiento"
        );
        byte[] bytes = createWorkbookBytes(swappedHeaders, List.of(List.of("B", "A", "C", "D", "E", "F", "G", "H")));

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(bytes, "test.xlsx"));
        assertTrue(ex.getMessage().contains("Cabeceras de plantilla inválidas"));
    }

    @Test
    @DisplayName("R1: Debe rechazar cabeceras con columnas adicionales")
    void testRejectExtraColumnsInHeader() throws IOException {
        List<String> extraHeaders = List.of(
                "nombre", "telefono", "correo", "service", "etapa", "priority", "origin", "proximoSeguimiento", "columnaExtra"
        );
        byte[] bytes = createWorkbookBytes(extraHeaders, List.of(List.of("A", "B", "C", "D", "E", "F", "G", "H", "I")));

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(bytes, "test.xlsx"));
        assertTrue(ex.getMessage().contains("Cabeceras de plantilla inválidas"));
    }

    @Test
    @DisplayName("R1: Debe rechazar archivo vacío o con tamaño 0")
    void testRejectEmptyFile() {
        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(new byte[0], "test.xlsx"));
        assertTrue(ex.getMessage().contains("El archivo es obligatorio"));

        assertThrows(LeadValidationException.class, () ->
                parser.parse(null, "test.xlsx"));
    }

    @Test
    @DisplayName("R1: Debe rechazar archivo que no es .xlsx por nombre o por bytes")
    void testRejectNonXlsx() {
        byte[] nonZipBytes = "Este es un archivo de texto plano".getBytes();
        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(nonZipBytes, "leads.csv"));
        assertTrue(ex.getMessage().contains("Solo se admiten archivos .xlsx"));

        // Extension dice .xlsx pero bytes no son PK
        LeadValidationException ex2 = assertThrows(LeadValidationException.class, () ->
                parser.parse(nonZipBytes, "leads.xlsx"));
        assertTrue(ex2.getMessage().contains("Solo se admiten archivos .xlsx"));
    }

    @Test
    @DisplayName("R1: Debe rechazar archivo que supera el límite de 5 MiB")
    void testRejectFileOver5MiB() {
        byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1];
        oversizedBytes[0] = 0x50;
        oversizedBytes[1] = 0x4B;
        oversizedBytes[2] = 0x03;
        oversizedBytes[3] = 0x04;

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(oversizedBytes, "leads.xlsx"));
        assertTrue(ex.getMessage().contains("supera el tamaño máximo"));
    }

    @Test
    @DisplayName("R1: Debe rechazar archivo que supera el límite de 500 filas de datos")
    void testRejectFileOver500Rows() throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 501; i++) {
            rows.add(List.of("Lead " + i, "987000" + i, "lead" + i + "@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"));
        }
        byte[] bytes = createWorkbookBytes(STANDARD_HEADERS, rows);

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(bytes, "large.xlsx"));
        assertTrue(ex.getMessage().contains("supera el límite máximo de 500 filas"));
    }

    @Test
    @DisplayName("R1: Archivo con exactamente 500 filas de datos debe ser aceptado")
    void testAcceptExact500Rows() throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            rows.add(List.of("Lead " + i, "987000" + i, "lead" + i + "@example.com", "Serv", "NUEVO", "MEDIA", "MANUAL", "2026-11-01"));
        }
        byte[] bytes = createWorkbookBytes(STANDARD_HEADERS, rows);

        List<ParsedRow> parsed = assertDoesNotThrow(() -> parser.parse(bytes, "500rows.xlsx"));
        assertEquals(500, parsed.size());
    }

    @Test
    @DisplayName("R3: Celdas con fórmulas no se evalúan y se detectan como tales")
    void testFormulaCellsFlagged() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Leads");
            XSSFRow header = sheet.createRow(0);
            for (int i = 0; i < STANDARD_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(STANDARD_HEADERS.get(i));
            }

            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue("Lead Con Formula");
            row.createCell(1).setCellValue("987654321");
            row.createCell(2).setCellValue("formula@example.com");
            XSSFCell formulaCell = row.createCell(3);
            formulaCell.setCellFormula("CONCATENATE(\"A\", \"B\")");
            row.createCell(4).setCellValue("NUEVO");
            row.createCell(5).setCellValue("MEDIA");
            row.createCell(6).setCellValue("MANUAL");
            row.createCell(7).setCellValue("2026-12-01");

            wb.write(out);
            bytes = out.toByteArray();
        }

        List<ParsedRow> rows = parser.parse(bytes, "formula.xlsx");
        assertEquals(1, rows.size());
        ParsedRow r = rows.get(0);
        assertTrue(r.formulasByField().containsKey("service"));
        assertTrue(r.formulasByField().get("service"));
    }

    @Test
    @DisplayName("R3: Celdas de fecha nativas de Excel se convierten a formato ISO YYYY-MM-DD")
    void testNativeExcelDateFormatting() throws IOException {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Leads");
            XSSFRow header = sheet.createRow(0);
            for (int i = 0; i < STANDARD_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(STANDARD_HEADERS.get(i));
            }

            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue("Lead Fecha");
            row.createCell(1).setCellValue("987654321");
            row.createCell(2).setCellValue("fecha@example.com");
            row.createCell(3).setCellValue("Servicio");
            row.createCell(4).setCellValue("NUEVO");
            row.createCell(5).setCellValue("MEDIA");
            row.createCell(6).setCellValue("MANUAL");

            XSSFCell dateCell = row.createCell(7);
            XSSFCellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            dateCell.setCellValue(LocalDate.of(2026, 11, 20));
            dateCell.setCellStyle(dateStyle);

            wb.write(out);
            bytes = out.toByteArray();
        }

        List<ParsedRow> rows = parser.parse(bytes, "dates.xlsx");
        assertEquals(1, rows.size());
        assertEquals("2026-11-20", rows.get(0).proximoSeguimiento());
    }

    @Test
    @DisplayName("R1: Rechaza archivos corruptos o dañados sin exponer trazas")
    void testCorruptedZipRejection() {
        byte[] corrupted = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x01, 0x02, 0x03};
        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(corrupted, "corrupted.xlsx"));
        assertEquals("No se pudo leer el archivo Excel o está dañado", ex.getMessage());
    }
}
