package com.urleads.comercial.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteStateMachineUnitTests {

    @Test
    @DisplayName("isFinal returns true only for ACCEPTED and REJECTED")
    void testIsFinal() {
        assertFalse(QuoteStatus.DRAFT.isFinal());
        assertFalse(QuoteStatus.SENT.isFinal());
        assertTrue(QuoteStatus.ACCEPTED.isFinal());
        assertTrue(QuoteStatus.REJECTED.isFinal());

        Quote quote = new Quote(101L, "42", "USD", new BigDecimal("0.18"), "Notas");
        assertFalse(quote.isFinal());

        quote.setStatus(QuoteStatus.SENT);
        assertFalse(quote.isFinal());

        quote.setStatus(QuoteStatus.ACCEPTED);
        assertTrue(quote.isFinal());

        quote.setStatus(QuoteStatus.REJECTED);
        assertTrue(quote.isFinal());
    }

    @Test
    @DisplayName("canTransitionTo validates valid transitions")
    void testValidTransitions() {
        // DRAFT -> SENT, ACCEPTED, REJECTED
        assertTrue(QuoteStatus.DRAFT.canTransitionTo(QuoteStatus.SENT));
        assertTrue(QuoteStatus.DRAFT.canTransitionTo(QuoteStatus.ACCEPTED));
        assertTrue(QuoteStatus.DRAFT.canTransitionTo(QuoteStatus.REJECTED));
        assertTrue(QuoteStatus.DRAFT.canTransitionTo(QuoteStatus.DRAFT)); // idempotente

        // SENT -> ACCEPTED, REJECTED
        assertTrue(QuoteStatus.SENT.canTransitionTo(QuoteStatus.ACCEPTED));
        assertTrue(QuoteStatus.SENT.canTransitionTo(QuoteStatus.REJECTED));
        assertTrue(QuoteStatus.SENT.canTransitionTo(QuoteStatus.SENT)); // idempotente

        // ACCEPTED -> ACCEPTED
        assertTrue(QuoteStatus.ACCEPTED.canTransitionTo(QuoteStatus.ACCEPTED));

        // REJECTED -> REJECTED
        assertTrue(QuoteStatus.REJECTED.canTransitionTo(QuoteStatus.REJECTED));
    }

    @Test
    @DisplayName("canTransitionTo invalidates illegal transitions")
    void testInvalidTransitions() {
        // SENT -> DRAFT
        assertFalse(QuoteStatus.SENT.canTransitionTo(QuoteStatus.DRAFT));

        // ACCEPTED -> DRAFT, SENT, REJECTED
        assertFalse(QuoteStatus.ACCEPTED.canTransitionTo(QuoteStatus.DRAFT));
        assertFalse(QuoteStatus.ACCEPTED.canTransitionTo(QuoteStatus.SENT));
        assertFalse(QuoteStatus.ACCEPTED.canTransitionTo(QuoteStatus.REJECTED));

        // REJECTED -> DRAFT, SENT, ACCEPTED
        assertFalse(QuoteStatus.REJECTED.canTransitionTo(QuoteStatus.DRAFT));
        assertFalse(QuoteStatus.REJECTED.canTransitionTo(QuoteStatus.SENT));
        assertFalse(QuoteStatus.REJECTED.canTransitionTo(QuoteStatus.ACCEPTED));

        // null target
        assertFalse(QuoteStatus.DRAFT.canTransitionTo(null));
    }

    @Test
    @DisplayName("Quote.transitionTo executes valid transitions smoothly")
    void testQuoteTransitionTo_valid() {
        Quote quote = new Quote(101L, "42", "USD", new BigDecimal("0.18"), "Notas");
        assertEquals(QuoteStatus.DRAFT, quote.getStatus());

        // DRAFT -> SENT
        quote.transitionTo(QuoteStatus.SENT);
        assertEquals(QuoteStatus.SENT, quote.getStatus());

        // SENT -> SENT (idempotent)
        assertDoesNotThrow(() -> quote.transitionTo(QuoteStatus.SENT));
        assertEquals(QuoteStatus.SENT, quote.getStatus());

        // SENT -> ACCEPTED
        quote.transitionTo(QuoteStatus.ACCEPTED);
        assertEquals(QuoteStatus.ACCEPTED, quote.getStatus());

        // ACCEPTED -> ACCEPTED (idempotent)
        assertDoesNotThrow(() -> quote.transitionTo(QuoteStatus.ACCEPTED));
        assertEquals(QuoteStatus.ACCEPTED, quote.getStatus());
    }

    @Test
    @DisplayName("Quote.transitionTo throws IllegalStateException on illegal transition")
    void testQuoteTransitionTo_illegal() {
        Quote quote = new Quote(101L, "42", "USD", new BigDecimal("0.18"), "Notas");
        quote.setStatus(QuoteStatus.ACCEPTED);

        // ACCEPTED -> REJECTED must throw IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> quote.transitionTo(QuoteStatus.REJECTED));
        assertTrue(ex.getMessage().contains("Transición inválida de estado"));

        // REJECTED -> SENT must throw IllegalStateException
        quote.setStatus(QuoteStatus.REJECTED);
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () -> quote.transitionTo(QuoteStatus.SENT));
        assertTrue(ex2.getMessage().contains("Transición inválida de estado"));
    }
}
