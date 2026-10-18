package com.urleads.comercial.dto;

import com.urleads.comercial.entity.QuoteTemplateItem;

import java.math.BigDecimal;

public record QuoteTemplateItemResponse(
    Long id,
    Long catalogItemId,
    String code,
    String name,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
    public static QuoteTemplateItemResponse fromEntity(QuoteTemplateItem item) {
        return new QuoteTemplateItemResponse(
            item.getId(),
            item.getCatalogItemId(),
            item.getCode(),
            item.getName(),
            item.getDescription(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getSubtotal()
        );
    }
}
