package com.urleads.comercial.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteTemplateUnitTests {

    @Test
    @DisplayName("QuoteTemplate calculates subtotal, tax, and total accurately from template items")
    void templateTotalsCalculation() {
        QuoteTemplate template = new QuoteTemplate(
            "42",
            "Plantilla Servicios Cloud",
            "Servicios mensuales recurrentes",
            "USD",
            new BigDecimal("0.1800"),
            "Condiciones estándar"
        );

        // Item 1: 2.00 qty * 150.00 = 300.00
        QuoteTemplateItem item1 = new QuoteTemplateItem(
            template, null, "SRV-01", "Servidor AWS", "Instancia EC2",
            new BigDecimal("2.00"), new BigDecimal("150.00")
        );
        template.addItem(item1);

        // Item 2: 1.50 qty * 200.33 = 300.495 -> 300.50
        QuoteTemplateItem item2 = new QuoteTemplateItem(
            template, null, "SRV-02", "Soporte DevOps", "Horas soporte",
            new BigDecimal("1.50"), new BigDecimal("200.33")
        );
        template.addItem(item2);

        // Subtotal = 300.00 + 300.50 = 600.50
        assertThat(template.getSubtotal()).isEqualByComparingTo(new BigDecimal("600.50"));
        // Tax = 600.50 * 0.18 = 108.09
        assertThat(template.getTax()).isEqualByComparingTo(new BigDecimal("108.09"));
        // Total = 600.50 + 108.09 = 708.59
        assertThat(template.getTotal()).isEqualByComparingTo(new BigDecimal("708.59"));
    }

    @Test
    @DisplayName("Deep copy creates distinct QuoteItem instances decoupled from QuoteTemplateItem")
    void deepCopyDecoupling() {
        QuoteTemplate template = new QuoteTemplate(
            "42",
            "Plantilla Consultoría",
            "Consultoría técnica",
            "PEN",
            new BigDecimal("0.1800"),
            "Notas de plantilla"
        );

        QuoteTemplateItem templateItem = new QuoteTemplateItem(
            template, 10L, "CONS-01", "Auditoría de Código", "Revisión arquitectónica",
            new BigDecimal("3.00"), new BigDecimal("500.00")
        );
        template.addItem(templateItem);

        // Create independent Quote from template
        Quote quote = new Quote(
            105L,
            template.getOwnerId(),
            template.getCurrency(),
            template.getTaxRate(),
            template.getNotes()
        );

        QuoteItem clonedItem = new QuoteItem(
            quote,
            templateItem.getCatalogItemId(),
            templateItem.getCode(),
            templateItem.getName(),
            templateItem.getDescription(),
            templateItem.getQuantity(),
            templateItem.getUnitPrice()
        );
        quote.addItem(clonedItem);

        // Verify values are equal initially
        assertThat(quote.getItems()).hasSize(1);
        assertThat(quote.getItems().get(0).getCode()).isEqualTo(templateItem.getCode());
        assertThat(quote.getItems().get(0).getQuantity()).isEqualByComparingTo(templateItem.getQuantity());
        assertThat(quote.getItems().get(0).getUnitPrice()).isEqualByComparingTo(templateItem.getUnitPrice());
        assertThat(quote.getSubtotal()).isEqualByComparingTo(template.getSubtotal());

        // Mutate quote item quantity and price
        quote.getItems().get(0).setQuantity(new BigDecimal("10.00"));
        quote.getItems().get(0).setUnitPrice(new BigDecimal("999.00"));
        quote.recalculateTotals();

        // Template item MUST remain unchanged!
        assertThat(templateItem.getQuantity()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(templateItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(template.getSubtotal()).isEqualByComparingTo(new BigDecimal("1500.00"));

        // Quote reflects new mutated totals
        assertThat(quote.getSubtotal()).isEqualByComparingTo(new BigDecimal("9990.00"));
    }
}
