package com.urleads.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.urleads.exception.LeadValidationException;

@Component
public class ExcelImportParser {

    public static final long MAX_UPLOAD_SIZE = 5 * 1024 * 1024; // 5 MiB (5,242,880 bytes)
    public static final int MAX_DATA_ROWS = 500;
    public static final long MAX_ENTRY_SIZE = 10L * 1024 * 1024; // 10 MiB bound for single entry
    public static final double MIN_INFLATE_RATIO = 0.01;
    public static final long MAX_FILE_COUNT = 100;
    private static final int MAX_CELL_STRING_LENGTH = 1000;

    public static final List<String> EXPECTED_HEADERS = List.of(
            "nombre",
            "telefono",
            "correo",
            "service",
            "etapa",
            "priority",
            "origin",
            "proximoSeguimiento"
    );

    public record ParsedRow(
            int rowNumber,
            String nombre,
            String telefono,
            String correo,
            String service,
            String etapa,
            String priority,
            String origin,
            String proximoSeguimiento,
            Map<String, Boolean> formulasByField,
            boolean hasExtraColumns
    ) {}

    public List<ParsedRow> parse(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw new LeadValidationException("El archivo es obligatorio y no puede estar vacío");
        }

        if (bytes.length > MAX_UPLOAD_SIZE) {
            throw new LeadValidationException("El archivo supera el tamaño máximo permitido de 5 MiB");
        }

        if (filename != null && !filename.trim().toLowerCase().endsWith(".xlsx")) {
            throw new LeadValidationException("Formato de archivo no válido. Solo se admiten archivos .xlsx");
        }

        // Validación estricta de número mágico ZIP (PK\003\004)
        if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B || bytes[2] != 0x03 || bytes[3] != 0x04) {
            throw new LeadValidationException("Formato de archivo no válido. Solo se admiten archivos .xlsx");
        }

        // Configuración defensiva contra Zip Bomb (acotada a 10 MiB por entrada y ratio 0.01)
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxFileCount(MAX_FILE_COUNT);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             OPCPackage pkg = OPCPackage.open(bais)) {

            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable sst = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            Iterator<InputStream> sheetIterator = reader.getSheetsData();
            if (!sheetIterator.hasNext()) {
                throw new LeadValidationException("El libro de Excel no contiene hojas de cálculo");
            }

            try (InputStream sheetStream = sheetIterator.next()) {
                XMLReader xmlReader = createSecureXmlReader();
                SheetHandler handler = new SheetHandler(sst, styles);
                xmlReader.setContentHandler(handler);
                xmlReader.parse(new InputSource(sheetStream));

                if (!handler.isHeaderValidated()) {
                    throw new LeadValidationException(
                            "Cabeceras de plantilla inválidas. Se requiere: nombre, telefono, correo, service, etapa, priority, origin, proximoSeguimiento");
                }

                return handler.getParsedRows();
            }

        } catch (LeadValidationException ex) {
            throw ex;
        } catch (OpenXML4JException | IOException | SAXException ex) {
            throw new LeadValidationException("No se pudo leer el archivo Excel o está dañado");
        } catch (Exception ex) {
            throw new LeadValidationException("No se pudo leer el archivo Excel o está dañado");
        }
    }

    XMLReader createSecureXmlReader() {
        return createSecureXmlReader(null);
    }

    XMLReader createSecureXmlReader(XMLReader customReader) {
        try {
            XMLReader xmlReader = (customReader != null) ? customReader : XMLHelper.newXMLReader();
            xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            xmlReader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return xmlReader;
        } catch (Exception e) {
            throw new LeadValidationException("No se pudo inicializar el procesador XML seguro");
        }
    }

    private static class SheetHandler extends DefaultHandler {

        private final ReadOnlySharedStringsTable sst;
        private final StylesTable styles;
        private final List<ParsedRow> parsedRows = new ArrayList<>();

        private boolean headerValidated = false;
        private int currentRowNum = 0;
        private int currentColIndex = -1;
        private String currentCellType;
        private int currentStyleIndex = -1;
        private boolean currentCellHasFormula = false;

        private boolean inFormulaTag = false;
        private boolean inValueTag = false;
        private boolean inTextTag = false;
        private final StringBuilder cellBuffer = new StringBuilder();

        private final String[] rowCells = new String[16];
        private final boolean[] rowFormulas = new boolean[16];
        private boolean rowHasExtraColumns = false;
        private int dataRowCount = 0;

        public SheetHandler(ReadOnlySharedStringsTable sst, StylesTable styles) {
            this.sst = sst;
            this.styles = styles;
        }

        public boolean isHeaderValidated() {
            return headerValidated;
        }

        public List<ParsedRow> getParsedRows() {
            return parsedRows;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            String tag = (localName != null && !localName.isEmpty()) ? localName : qName;

            if ("row".equals(tag)) {
                String rAttr = attributes.getValue("r");
                if (rAttr != null) {
                    try {
                        currentRowNum = Integer.parseInt(rAttr);
                    } catch (NumberFormatException e) {
                        currentRowNum++;
                    }
                } else {
                    currentRowNum++;
                }

                Arrays.fill(rowCells, null);
                Arrays.fill(rowFormulas, false);
                rowHasExtraColumns = false;
            } else if ("c".equals(tag)) {
                String rAttr = attributes.getValue("r");
                currentColIndex = parseColumnIndex(rAttr);
                currentCellType = attributes.getValue("t");
                String sAttr = attributes.getValue("s");
                currentStyleIndex = sAttr != null ? parseStyleIndex(sAttr) : -1;
                currentCellHasFormula = false;
                cellBuffer.setLength(0);
            } else if ("f".equals(tag)) {
                inFormulaTag = true;
                currentCellHasFormula = true;
            } else if ("v".equals(tag)) {
                inValueTag = true;
            } else if ("t".equals(tag)) {
                inTextTag = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValueTag || inTextTag) {
                if (cellBuffer.length() < MAX_CELL_STRING_LENGTH) {
                    int toAppend = Math.min(length, MAX_CELL_STRING_LENGTH - cellBuffer.length());
                    cellBuffer.append(ch, start, toAppend);
                }
            }
            if (inFormulaTag) {
                currentCellHasFormula = true;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String tag = (localName != null && !localName.isEmpty()) ? localName : qName;

            if ("f".equals(tag)) {
                inFormulaTag = false;
            } else if ("v".equals(tag)) {
                inValueTag = false;
            } else if ("t".equals(tag)) {
                inTextTag = false;
            } else if ("c".equals(tag)) {
                if (currentColIndex >= 0) {
                    String formattedValue = resolveCellValue(cellBuffer.toString(), currentCellType, currentStyleIndex);
                    if (currentColIndex < 8) {
                        rowCells[currentColIndex] = formattedValue;
                        rowFormulas[currentColIndex] = currentCellHasFormula;
                    } else {
                        if (formattedValue != null && !formattedValue.isBlank()) {
                            rowHasExtraColumns = true;
                        }
                    }
                }
            } else if ("row".equals(tag)) {
                processRow();
            }
        }

        private void processRow() {
            if (currentRowNum == 1) {
                // Validación estricta de la fila de cabecera
                if (rowHasExtraColumns) {
                    throw new LeadValidationException(
                            "Cabeceras de plantilla inválidas. Se requiere: nombre, telefono, correo, service, etapa, priority, origin, proximoSeguimiento");
                }
                for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
                    String actualHeader = rowCells[i];
                    if (actualHeader == null || !actualHeader.trim().equals(EXPECTED_HEADERS.get(i))) {
                        throw new LeadValidationException(
                                "Cabeceras de plantilla inválidas. Se requiere: nombre, telefono, correo, service, etapa, priority, origin, proximoSeguimiento");
                    }
                }
                headerValidated = true;
            } else if (currentRowNum > 1) {
                if (!headerValidated) {
                    throw new LeadValidationException(
                            "Cabeceras de plantilla inválidas. Se requiere: nombre, telefono, correo, service, etapa, priority, origin, proximoSeguimiento");
                }

                // Verificar si la fila está completamente vacía
                boolean entirelyBlank = true;
                for (int i = 0; i < 8; i++) {
                    if (rowCells[i] != null && !rowCells[i].isBlank()) {
                        entirelyBlank = false;
                        break;
                    }
                    if (rowFormulas[i]) {
                        entirelyBlank = false;
                        break;
                    }
                }
                if (rowHasExtraColumns) {
                    entirelyBlank = false;
                }

                // Política de filas vacías: ignorar de forma segura si está 100% vacía
                if (entirelyBlank) {
                    return;
                }

                dataRowCount++;
                if (dataRowCount > MAX_DATA_ROWS) {
                    throw new LeadValidationException("El archivo supera el límite máximo de 500 filas de datos");
                }

                Map<String, Boolean> formulasByField = new HashMap<>();
                for (int i = 0; i < 8; i++) {
                    if (rowFormulas[i]) {
                        formulasByField.put(EXPECTED_HEADERS.get(i), true);
                    }
                }

                parsedRows.add(new ParsedRow(
                        currentRowNum,
                        cleanValue(rowCells[0]),
                        cleanValue(rowCells[1]),
                        cleanValue(rowCells[2]),
                        cleanValue(rowCells[3]),
                        cleanValue(rowCells[4]),
                        cleanValue(rowCells[5]),
                        cleanValue(rowCells[6]),
                        cleanValue(rowCells[7]),
                        formulasByField,
                        rowHasExtraColumns
                ));
            }
        }

        private String cleanValue(String val) {
            if (val == null) {
                return null;
            }
            String trimmed = val.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private int parseStyleIndex(String sAttr) {
            try {
                return Integer.parseInt(sAttr);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        private int parseColumnIndex(String cellRef) {
            if (cellRef == null || cellRef.isEmpty()) {
                return -1;
            }
            int col = 0;
            boolean found = false;
            for (int i = 0; i < cellRef.length(); i++) {
                char c = cellRef.charAt(i);
                if (Character.isLetter(c)) {
                    col = col * 26 + (Character.toUpperCase(c) - 'A' + 1);
                    found = true;
                } else {
                    break;
                }
            }
            return found ? (col - 1) : -1;
        }

        private String resolveCellValue(String raw, String cellType, int styleIndex) {
            if (raw == null) {
                return null;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                return null;
            }

            if ("s".equals(cellType)) {
                try {
                    int sstIdx = Integer.parseInt(trimmed);
                    RichTextString rts = sst.getItemAt(sstIdx);
                    return rts != null ? rts.getString() : "";
                } catch (Exception e) {
                    return trimmed;
                }
            } else if ("b".equals(cellType)) {
                return "1".equals(trimmed) ? "true" : "false";
            } else if ("e".equals(cellType)) {
                return "#ERROR";
            }

            // Detección de fecha según formato de celda
            if (isDateFormat(styleIndex)) {
                try {
                    double d = Double.parseDouble(trimmed);
                    LocalDate ld = DateUtil.getLocalDateTime(d).toLocalDate();
                    return ld.toString();
                } catch (Exception ignored) {
                }
            }

            // Si es un número entero (ej. número telefónico sin formato especial)
            try {
                double d = Double.parseDouble(trimmed);
                if (d == Math.floor(d) && !Double.isInfinite(d) && d <= Long.MAX_VALUE && d >= Long.MIN_VALUE) {
                    return Long.toString((long) d);
                }
            } catch (NumberFormatException ignored) {
            }

            return trimmed;
        }

        private boolean isDateFormat(int styleIdx) {
            if (styles == null || styleIdx < 0) {
                return false;
            }
            try {
                XSSFCellStyle style = styles.getStyleAt(styleIdx);
                if (style == null) {
                    return false;
                }
                int formatIndex = style.getDataFormat();
                String formatString = style.getDataFormatString();
                return DateUtil.isADateFormat(formatIndex, formatString);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
