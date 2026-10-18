package com.urleads.comercial.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteCalculationUnitTests {

    @Test
    @DisplayName("Empty quote calculates zero subtotal, tax, and total")
    void emptyQuoteCalculations() {
        Quote quote = new Quote(100L, "42", "USD", new BigDecimal("0.1800"), "Empty");
        quote.recalculateTotals();

        assertThat(quote.getSubtotal()).isEqualByComparingTo("0.00");
        assertThat(quote.getTax()).isEqualByComparingTo("0.00");
        assertThat(quote.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Fractional quantity with half-up rounding on line items and taxes")
    void fractionalQuantityCalculations() {
        Quote quote = new Quote(100L, "42", "PEN", new BigDecimal("0.1800"), "Test");

        // Line 1: 3.33 units * 12.50 = 41.625 -> rounds to 41.63
        QuoteItem item1 = new QuoteItem(quote, null, "C1", "Item 1", "", new BigDecimal("3.33"), new BigDecimal("12.50"));
        quote.addItem(item1);

        assertThat(item1.getSubtotal()).isEqualByComparingTo("41.63");

        // Line 2: 0.13 units * 100.00 = 13.00
        QuoteItem item2 = new QuoteItem(quote, null, "C2", "Item 2", "", new BigDecimal("0.13"), new BigDecimal("100.00"));
        quote.addItem(item2);

        // Subtotal = 41.63 + 13.00 = 54.63
        // Tax (18%) = 54.63 * 0.1800 = 9.8334 -> rounds to 9.83
        // Total = 54.63 + 9.83 = 64.46
        assertThat(quote.getSubtotal()).isEqualByComparingTo("54.63");
        assertThat(quote.getTax()).isEqualByComparingTo("9.83");
        assertThat(quote.getTotal()).isEqualByComparingTo("64.46");
    }

    @Test
    @DisplayName("Exact half-up boundary cases (.375 rounds up to .38)")
    void halfUpExactBoundary() {
        Quote quote = new Quote(100L, "42", "USD", new BigDecimal("0.1000"), "Tax 10%");

        // 2.50 units * 12.55 = 31.375 -> exactly halfway, rounds up to 31.38
        QuoteItem item = new QuoteItem(quote, null, "EDGE", "Edge", "", new BigDecimal("2.50"), new BigDecimal("12.55"));
        quote.addItem(item);

        assertThat(item.getSubtotal()).isEqualByComparingTo("31.38");
        assertThat(quote.getSubtotal()).isEqualByComparingTo("31.38");
        // 31.38 * 0.10 = 3.138 -> rounds up to 3.14
        assertThat(quote.getTax()).isEqualByComparingTo("3.14");
        // Total = 31.38 + 3.14 = 34.52
        assertThat(quote.getTotal()).isEqualByComparingTo("34.52");
    }

    @Test
    @DisplayName("Adding and removing items recalculates totals accurately")
    void addAndRemoveItemRecalculates() {
        Quote quote = new Quote(100L, "42", "USD", new BigDecimal("0.1800"), "Test");

        QuoteItem itm1 = new QuoteItem(quote, null, "A", "A", "", new BigDecimal("1.00"), new BigDecimal("100.00"));
        QuoteItem itm2 = new QuoteItem(quote, null, "B", "B", "", new BigDecimal("2.00"), new BigDecimal("50.00"));

        quote.addItem(itm1);
        assertThat(quote.getTotal()).isEqualByComparingTo("118.00");

        quote.addItem(itm2);
        assertThat(quote.getTotal()).isEqualByComparingTo("236.00");

        quote.removeItem(itm1);
        assertThat(quote.getTotal()).isEqualByComparingTo("118.00");
    }
}
