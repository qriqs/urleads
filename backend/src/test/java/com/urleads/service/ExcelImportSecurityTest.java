package com.urleads.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import com.urleads.exception.LeadValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ExcelImportSecurityTest {

    private ExcelImportParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExcelImportParser();
    }

    private byte[] createBaseWorkbook() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Leads");
            XSSFRow header = sheet.createRow(0);
            List<String> headers = List.of(
                    "nombre", "telefono", "correo", "service", "etapa", "priority", "origin", "proximoSeguimiento"
            );
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] replaceZipEntry(byte[] zipBytes, String targetEntryName, String newContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(targetEntryName)) {
                    ZipEntry newEntry = new ZipEntry(targetEntryName);
                    zos.putNextEntry(newEntry);
                    zos.write(newContent.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                } else {
                    ZipEntry clone = new ZipEntry(entry.getName());
                    zos.putNextEntry(clone);
                    zis.transferTo(zos);
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }

    private byte[] createZipWithStreamingEntry(byte[] zipBytes, String targetEntryName, byte[] chunk, int repeatCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(targetEntryName)) {
                    ZipEntry newEntry = new ZipEntry(targetEntryName);
                    zos.putNextEntry(newEntry);
                    zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><!--".getBytes(StandardCharsets.UTF_8));
                    for (int i = 0; i < repeatCount; i++) {
                        zos.write(chunk);
                    }
                    zos.write("--><sheetData><row r=\"1\"><c r=\"A1\" t=\"str\"><v>Test</v></c></row></sheetData></worksheet>".getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                } else {
                    ZipEntry clone = new ZipEntry(entry.getName());
                    zos.putNextEntry(clone);
                    zis.transferTo(zos);
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("R2: Intento de XXE con DOCTYPE y entidad externa es rechazado de forma segura")
    void testXxeExternalEntityRejected() throws IOException {
        byte[] baseBytes = createBaseWorkbook();

        String xxeSheetXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<!DOCTYPE worksheet [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                "  <sheetData>\n" +
                "    <row r=\"1\">\n" +
                "      <c r=\"A1\" t=\"str\"><v>&xxe;</v></c>\n" +
                "    </row>\n" +
                "  </sheetData>\n" +
                "</worksheet>";

        byte[] maliciousXlsx = replaceZipEntry(baseBytes, "xl/worksheets/sheet1.xml", xxeSheetXml);

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(maliciousXlsx, "malicious.xlsx"));

        assertEquals("No se pudo leer el archivo Excel o está dañado", ex.getMessage());
    }

    @Test
    @DisplayName("R2: Intento de XXE con DTD externa vía HTTP es bloqueado sin resolución de red")
    void testXxeExternalDtdRejected() throws IOException {
        byte[] baseBytes = createBaseWorkbook();

        String xxeDtdXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<!DOCTYPE worksheet SYSTEM \"http://127.0.0.1:54321/evil.dtd\">\n" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                "  <sheetData>\n" +
                "    <row r=\"1\">\n" +
                "      <c r=\"A1\" t=\"str\"><v>Test</v></c>\n" +
                "    </row>\n" +
                "  </sheetData>\n" +
                "</worksheet>";

        byte[] maliciousXlsx = replaceZipEntry(baseBytes, "xl/worksheets/sheet1.xml", xxeDtdXml);

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(maliciousXlsx, "external-dtd.xlsx"));

        assertEquals("No se pudo leer el archivo Excel o está dañado", ex.getMessage());
    }

    @Test
    @DisplayName("R2: Configuración de límites conservadores de ZipSecureFile (10 MiB max entry, ratio 0.01, 100 archivos)")
    void testZipSecureFileDefensesConfigured() throws IOException {
        byte[] validBytes = createBaseWorkbook();
        parser.parse(validBytes, "check.xlsx");

        assertEquals(ExcelImportParser.MIN_INFLATE_RATIO, ZipSecureFile.getMinInflateRatio(), 0.001);
        assertEquals(ExcelImportParser.MAX_ENTRY_SIZE, ZipSecureFile.getMaxEntrySize());
        assertEquals(ExcelImportParser.MAX_FILE_COUNT, ZipSecureFile.getMaxFileCount());
    }

    @Test
    @DisplayName("R2: Archivo con ratio de inflado anómalo (zip bomb) es rechazado por ZipSecureFile de forma compacta")
    void testAdversarialZipBombRatioRejected() throws IOException {
        byte[] baseBytes = createBaseWorkbook();

        byte[] chunk = new byte[4096];
        Arrays.fill(chunk, (byte) 'A');
        // 4 KB * 128 = 512 KB sin comprimir (supera grace size de 100 KB; ratio de compresión < 0.005)
        byte[] bombBytes = createZipWithStreamingEntry(baseBytes, "xl/worksheets/sheet1.xml", chunk, 128);

        assertTrue(bombBytes.length < 50 * 1024, "El archivo comprimido de prueba debe ser compacto (<50 KB)");

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(bombBytes, "zipbomb.xlsx"));

        assertEquals("No se pudo leer el archivo Excel o está dañado", ex.getMessage());
    }

    @Test
    @DisplayName("R2: Archivo con entrada que supera MAX_ENTRY_SIZE (10 MiB) es rechazado de forma compacta")
    void testOversizedZipEntryRejected() throws IOException {
        byte[] baseBytes = createBaseWorkbook();

        byte[] chunk = new byte[8192];
        Arrays.fill(chunk, (byte) 'Z');
        // 8 KB * 1350 = 11,059,200 bytes (> 10 MiB limit) generado de forma streaming sin alocar 11 MiB en heap
        byte[] oversizedBytes = createZipWithStreamingEntry(baseBytes, "xl/worksheets/sheet1.xml", chunk, 1350);

        assertTrue(oversizedBytes.length < 100 * 1024, "El archivo comprimido debe ser compacto (<100 KB)");

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.parse(oversizedBytes, "oversized.xlsx"));

        assertEquals("No se pudo leer el archivo Excel o está dañado", ex.getMessage());
    }

    @Test
    @DisplayName("R2: Falla al configurar características de seguridad XML debe fallar cerrado (LeadValidationException)")
    void testFailClosedWhenSecurityFeatureCannotBeSet() throws Exception {
        XMLReader faultyReader = mock(XMLReader.class);
        doThrow(new SAXNotSupportedException("Unsupported feature"))
                .when(faultyReader).setFeature(anyString(), anyBoolean());

        LeadValidationException ex = assertThrows(LeadValidationException.class, () ->
                parser.createSecureXmlReader(faultyReader));

        assertEquals("No se pudo inicializar el procesador XML seguro", ex.getMessage());
    }
}
