package com.urleads.comercial.dto;

import com.urleads.comercial.entity.QuoteItem;
import java.math.BigDecimal;

public record QuoteItemResponse(
    Long id,
    Long catalogItemId,
    String code,
    String name,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
    public static QuoteItemResponse fromEntity(QuoteItem item) {
        return new QuoteItemResponse(
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
