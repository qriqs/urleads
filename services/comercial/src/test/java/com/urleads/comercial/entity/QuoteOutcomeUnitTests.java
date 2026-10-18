package com.urleads.comercial.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuoteOutcomeUnitTests {

    @Test
    @DisplayName("QuoteOutcomeReason contains all structured commercial closing reasons")
    void testEnumValues() {
        assertEquals(5, QuoteOutcomeReason.values().length);
        assertNotNull(QuoteOutcomeReason.valueOf("PRECIO"));
        assertNotNull(QuoteOutcomeReason.valueOf("COMPETENCIA"));
        assertNotNull(QuoteOutcomeReason.valueOf("PLAZOS"));
        assertNotNull(QuoteOutcomeReason.valueOf("DESCARTE"));
        assertNotNull(QuoteOutcomeReason.valueOf("OTRO"));
    }

    @Test
    @DisplayName("Quote entity correctly stores and retrieves outcome fields")
    void testQuoteOutcomeFields() {
        Quote quote = new Quote(101L, "42", "USD", new BigDecimal("0.1800"), "Notas iniciales");

        assertNull(quote.getOutcomeReason());
        assertNull(quote.getOutcomeNotes());
        assertNull(quote.getOutcomeDate());

        Instant now = Instant.now();
        quote.setStatus(QuoteStatus.REJECTED);
        quote.setOutcomeReason(QuoteOutcomeReason.PRECIO);
        quote.setOutcomeNotes("Precio por encima del presupuesto del cliente");
        quote.setOutcomeDate(now);

        assertEquals(QuoteStatus.REJECTED, quote.getStatus());
        assertEquals(QuoteOutcomeReason.PRECIO, quote.getOutcomeReason());
        assertEquals("Precio por encima del presupuesto del cliente", quote.getOutcomeNotes());
        assertEquals(now, quote.getOutcomeDate());
    }
}
