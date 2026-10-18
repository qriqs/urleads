package com.urleads.comercial.dto;

import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record QuoteResponse(
    Long id,
    Long leadId,
    String ownerId,
    String currency,
    QuoteStatus status,
    BigDecimal subtotal,
    BigDecimal taxRate,
    BigDecimal tax,
    BigDecimal total,
    String notes,
    int itemsCount,
    Instant createdAt,
    Instant updatedAt
) {
    public static QuoteResponse fromEntity(Quote quote) {
        return new QuoteResponse(
            quote.getId(),
            quote.getLeadId(),
            quote.getOwnerId(),
            quote.getCurrency(),
            quote.getStatus(),
            quote.getSubtotal(),
            quote.getTaxRate(),
            quote.getTax(),
            quote.getTotal(),
            quote.getNotes(),
            quote.getItems() != null ? quote.getItems().size() : 0,
            quote.getCreatedAt(),
            quote.getUpdatedAt()
        );
    }
}
