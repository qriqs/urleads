package com.urleads.comercial.dto;

import com.urleads.comercial.entity.CommercialOrderItem;

import java.math.BigDecimal;

public record CommercialOrderItemResponse(
    Long id,
    Long catalogItemId,
    String code,
    String name,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
    public static CommercialOrderItemResponse fromEntity(CommercialOrderItem item) {
        return new CommercialOrderItemResponse(
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
