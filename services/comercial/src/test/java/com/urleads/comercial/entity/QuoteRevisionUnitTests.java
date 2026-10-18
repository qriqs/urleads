package com.urleads.comercial.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteRevisionUnitTests {

    @Test
    @DisplayName("Quote.createNextRevision increments revisionNumber, links parent/root, and sets isLatest flags")
    void createNextRevision_metadataAndHierarchy() {
        Quote v1 = new Quote(101L, "42", "USD", new BigDecimal("0.1800"), "Cotización inicial v1");
        v1.setId(10L);
        v1.setRootQuoteId(10L);
        v1.setRevisionNumber(1);
        v1.setLatest(true);

        QuoteItem item1 = new QuoteItem(v1, null, "ITM-01", "Servicio Base", "Detalle", new BigDecimal("1.00"), new BigDecimal("100.00"));
        v1.addItem(item1);

        // Create revision v2
        Quote v2 = v1.createNextRevision("Revisión v2 con descuento", new BigDecimal("0.1000"));
        v2.setId(20L);

        // Assert v1 state
        assertThat(v1.isLatest()).isFalse();
        assertThat(v1.getRevisionNumber()).isEqualTo(1);
        assertThat(v1.getRootQuoteId()).isEqualTo(10L);

        // Assert v2 state
        assertThat(v2.isLatest()).isTrue();
        assertThat(v2.getRevisionNumber()).isEqualTo(2);
        assertThat(v2.getRootQuoteId()).isEqualTo(10L);
        assertThat(v2.getParentQuoteId()).isEqualTo(10L);
        assertThat(v2.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(v2.getNotes()).isEqualTo("Revisión v2 con descuento");
        assertThat(v2.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.1000"));

        // Create revision v3 from v2
        Quote v3 = v2.createNextRevision(null, null);
        v3.setId(30L);

        assertThat(v2.isLatest()).isFalse();
        assertThat(v3.isLatest()).isTrue();
        assertThat(v3.getRevisionNumber()).isEqualTo(3);
        assertThat(v3.getRootQuoteId()).isEqualTo(10L);
        assertThat(v3.getParentQuoteId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Quote.createNextRevision performs deep clone of items, preventing cross-version mutation")
    void createNextRevision_deepCloningDecoupling() {
        Quote v1 = new Quote(105L, "42", "PEN", new BigDecimal("0.1800"), "Notas v1");
        v1.setId(50L);
        v1.setRootQuoteId(50L);

        QuoteItem itemV1 = new QuoteItem(v1, 5L, "DEV-01", "Desarrollo", "Horas", new BigDecimal("5.00"), new BigDecimal("120.00"));
        v1.addItem(itemV1);

        Quote v2 = v1.createNextRevision("Notas v2", null);
        v2.setId(60L);

        assertThat(v2.getItems()).hasSize(1);
        QuoteItem itemV2 = v2.getItems().get(0);

        // Verify cloned values
        assertThat(itemV2.getCode()).isEqualTo("DEV-01");
        assertThat(itemV2.getQuantity()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(itemV2.getUnitPrice()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(itemV2.getQuote()).isSameAs(v2);

        // Mutate v2 item
        itemV2.setQuantity(new BigDecimal("10.00"));
        itemV2.setUnitPrice(new BigDecimal("200.00"));
        v2.recalculateTotals();

        // Verify v1 item is untouched
        assertThat(itemV1.getQuantity()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(itemV1.getUnitPrice()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(v1.getSubtotal()).isEqualByComparingTo(new BigDecimal("600.00"));

        // Verify v2 reflects updated values
        assertThat(v2.getSubtotal()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }
}
