package com.urleads.comercial.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class QuoteImmutableSnapshotUnitTests {

    @Test
    @DisplayName("QuoteItem captures snapshot of CatalogItem and remains immutable when CatalogItem changes")
    void testCatalogSnapshotImmutability() {
        CatalogItem catalogItem = new CatalogItem(
            "42",
            "SRV-CONSULT",
            "Consultoria Cloud",
            "Servicios iniciales de consultoria",
            "USD",
            new BigDecimal("120.00"),
            true
        );

        Quote quote = new Quote(105L, "42", "USD", new BigDecimal("0.1800"), "Notas");

        // Crear snapshot en linea de cotización
        QuoteItem snapshotItem = QuoteItem.fromCatalogSnapshot(
            quote,
            catalogItem,
            new BigDecimal("3.00"),
            null
        );

        assertEquals("SRV-CONSULT", snapshotItem.getCode());
        assertEquals("Consultoria Cloud", snapshotItem.getName());
        assertEquals("Servicios iniciales de consultoria", snapshotItem.getDescription());
        assertEquals(new BigDecimal("120.00"), snapshotItem.getUnitPrice());
        assertEquals(new BigDecimal("3.00"), snapshotItem.getQuantity());
        assertEquals(new BigDecimal("360.00"), snapshotItem.getSubtotal());

        // Modificar fuertemente el item del catalogo posterior a la inclusion
        catalogItem.setName("Consultoria Cloud V2 Enterprise");
        catalogItem.setDescription("Descripcion nueva y modificada");
        catalogItem.setUnitPrice(new BigDecimal("500.00"));
        catalogItem.setActive(false);

        // El snapshot debe permanecer completamente inmutable
        assertEquals("SRV-CONSULT", snapshotItem.getCode());
        assertEquals("Consultoria Cloud", snapshotItem.getName());
        assertEquals("Servicios iniciales de consultoria", snapshotItem.getDescription());
        assertEquals(new BigDecimal("120.00"), snapshotItem.getUnitPrice());
        assertEquals(new BigDecimal("360.00"), snapshotItem.getSubtotal());

        assertNotEquals(catalogItem.getName(), snapshotItem.getName());
        assertNotEquals(catalogItem.getUnitPrice(), snapshotItem.getUnitPrice());
        assertNotEquals(catalogItem.getDescription(), snapshotItem.getDescription());
    }

    @Test
    @DisplayName("QuoteItem allows overriding unit price while snapshotting name and description")
    void testCatalogSnapshotWithPriceOverride() {
        CatalogItem catalogItem = new CatalogItem(
            "42",
            "SRV-HOST",
            "Hosting Dedicado",
            "Servidor en nube",
            "PEN",
            new BigDecimal("200.00"),
            true
        );

        Quote quote = new Quote(101L, "42", "PEN", new BigDecimal("0.1800"), "");

        // Snapshot con precio unitario acordado con descuento (180.00 en vez de 200.00)
        QuoteItem snapshotItem = QuoteItem.fromCatalogSnapshot(
            quote,
            catalogItem,
            new BigDecimal("2.00"),
            new BigDecimal("180.00")
        );

        assertEquals("SRV-HOST", snapshotItem.getCode());
        assertEquals("Hosting Dedicado", snapshotItem.getName());
        assertEquals(new BigDecimal("180.00"), snapshotItem.getUnitPrice());
        assertEquals(new BigDecimal("360.00"), snapshotItem.getSubtotal());
    }
}
