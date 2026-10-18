package com.urleads.comercial.dto;

import com.urleads.comercial.entity.CatalogItem;

import java.math.BigDecimal;
import java.time.Instant;

public record CatalogItemResponse(
    Long id,
    String code,
    String name,
    String description,
    String currency,
    BigDecimal unitPrice,
    Boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static CatalogItemResponse fromEntity(CatalogItem item) {
        return new CatalogItemResponse(
            item.getId(),
            item.getCode(),
            item.getName(),
            item.getDescription(),
            item.getCurrency(),
            item.getUnitPrice(),
            item.getActive(),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}
