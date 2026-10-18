package com.urleads.comercial.dto;

import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteOutcomeReason;
import com.urleads.comercial.entity.QuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuoteDetailResponse(
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
    List<QuoteItemResponse> items,
    Instant createdAt,
    Instant updatedAt,
    QuoteOutcomeReason outcomeReason,
    String outcomeNotes,
    Instant outcomeDate,
    Long version,
    Integer revisionNumber,
    Boolean isLatest,
    Long rootQuoteId,
    Long parentQuoteId
) {
    public static QuoteDetailResponse fromEntity(Quote quote) {
        List<QuoteItemResponse> itemResponses = quote.getItems() != null
            ? quote.getItems().stream().map(QuoteItemResponse::fromEntity).toList()
            : List.of();

        return new QuoteDetailResponse(
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
            itemResponses,
            quote.getCreatedAt(),
            quote.getUpdatedAt(),
            quote.getOutcomeReason(),
            quote.getOutcomeNotes(),
            quote.getOutcomeDate(),
            quote.getVersion(),
            quote.getRevisionNumber(),
            quote.isLatest(),
            quote.getRootQuoteId(),
            quote.getParentQuoteId()
        );
    }
}
