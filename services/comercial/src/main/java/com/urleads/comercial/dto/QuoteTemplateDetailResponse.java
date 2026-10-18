package com.urleads.comercial.dto;

import com.urleads.comercial.entity.QuoteTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuoteTemplateDetailResponse(
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
    List<QuoteTemplateItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
    public static QuoteTemplateDetailResponse fromEntity(QuoteTemplate template) {
        List<QuoteTemplateItemResponse> itemDtos = template.getItems() != null
            ? template.getItems().stream().map(QuoteTemplateItemResponse::fromEntity).toList()
            : List.of();

        return new QuoteTemplateDetailResponse(
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
            itemDtos,
            template.getCreatedAt(),
            template.getUpdatedAt()
        );
    }
}
