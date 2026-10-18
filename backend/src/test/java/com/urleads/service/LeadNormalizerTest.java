package com.urleads.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("R1/R2: Pruebas unitarias para normalización de correo y teléfono")
class LeadNormalizerTest {

    @Test
    @DisplayName("R1: normalizeEmail convierte a minúsculas y recorta espacios, tabuladores y caracteres de control ASCII (<= U+0020)")
    void normalizeEmail_conEspaciosYMayusculas_retornaMinusculasTrimmed() {
        assertThat(LeadNormalizer.normalizeEmail("  Ana.Torres@Example.COM  "))
                .isEqualTo("ana.torres@example.com");
        assertThat(LeadNormalizer.normalizeEmail("USER+TAG@SUB.DOMAIN.ORG"))
                .isEqualTo("user+tag@sub.domain.org");
        assertThat(LeadNormalizer.normalizeEmail("\t\n  Ana.Torres@Example.COM  \r\n"))
                .isEqualTo("ana.torres@example.com");
        assertThat(LeadNormalizer.normalizeEmail("\u0001\u001F  Ana.Torres@Example.COM  \u0002"))
                .isEqualTo("ana.torres@example.com");
    }

    @Test
    @DisplayName("R1: normalizeEmail con null, vacío o solo espacios retorna null (ausente)")
    void normalizeEmail_ausenteOBlanco_retornaNull() {
        assertThat(LeadNormalizer.normalizeEmail(null)).isNull();
        assertThat(LeadNormalizer.normalizeEmail("")).isNull();
        assertThat(LeadNormalizer.normalizeEmail("   ")).isNull();
        assertThat(LeadNormalizer.normalizeEmail("\t\n\r")).isNull();
    }

    @Test
    @DisplayName("R2: normalizePhone extrae exclusivamente dígitos ASCII de formatos variados")
    void normalizePhone_formatosVariados_extraeDigitosAscii() {
        assertThat(LeadNormalizer.normalizePhone("+1 (212) 555-0100")).isEqualTo("12125550100");
        assertThat(LeadNormalizer.normalizePhone("1.212.555.0100")).isEqualTo("12125550100");
        assertThat(LeadNormalizer.normalizePhone("+51 999 111 222")).isEqualTo("51999111222");
        assertThat(LeadNormalizer.normalizePhone("  999-111-222  ")).isEqualTo("999111222");
        assertThat(LeadNormalizer.normalizePhone("(01) 444 5555")).isEqualTo("014445555");
    }

    @Test
    @DisplayName("R2: normalizePhone con null, vacío, solo espacios o sin dígitos retorna null (ausente)")
    void normalizePhone_ausenteOSinDigitos_retornaNull() {
        assertThat(LeadNormalizer.normalizePhone(null)).isNull();
        assertThat(LeadNormalizer.normalizePhone("")).isNull();
        assertThat(LeadNormalizer.normalizePhone("   ")).isNull();
        assertThat(LeadNormalizer.normalizePhone("\t\n\r")).isNull();
        assertThat(LeadNormalizer.normalizePhone("---")).isNull();
        assertThat(LeadNormalizer.normalizePhone("+ - () / .")).isNull();
        assertThat(LeadNormalizer.normalizePhone("teléfono")).isNull();
    }

    @Test
    @DisplayName("R2: normalizePhone ignora dígitos no ASCII (ej. árabes o fullwidth)")
    void normalizePhone_digitosNoAscii_ignoraYRetornaSoloAsciiONull() {
        // '\u0660' (0 árabe), '\u0661' (1 árabe)
        assertThat(LeadNormalizer.normalizePhone("\u0660\u0661\u0662")).isNull();
        // Mezclado con ASCII: "tel: \u0660 999 \u0661" -> solo "999"
        assertThat(LeadNormalizer.normalizePhone("tel: \u0660 999 \u0661")).isEqualTo("999");
        // '\uFF11' (1 fullwidth)
        assertThat(LeadNormalizer.normalizePhone("\uFF11\uFF12\uFF13")).isNull();
    }
}
