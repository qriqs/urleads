package com.urleads.comercial.dto;

import com.urleads.comercial.entity.QuoteTemplate;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteTemplateResponse(
    Long id,
    String ownerId,
    String name,
    String description,
    String currency,
    BigDecimal taxRate,
    String notes,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal total,
    int itemsCount,
    Instant createdAt,
    Instant updatedAt
) {
    public static QuoteTemplateResponse fromEntity(QuoteTemplate template) {
        return new QuoteTemplateResponse(
            template.getId(),
            template.getOwnerId(),
            template.getName(),
            template.getDescription(),
            template.getCurrency(),
            template.getTaxRate(),
            template.getNotes(),
            template.getSubtotal(),
            template.getTax(),
            template.getTotal(),
            template.getItems() != null ? template.getItems().size() : 0,
            template.getCreatedAt(),
            template.getUpdatedAt()
        );
    }
}
