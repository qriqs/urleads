package com.urleads.comercial.dto;

import com.urleads.comercial.entity.CommercialOrder;
import com.urleads.comercial.entity.CommercialOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CommercialOrderResponse(
    Long id,
    String orderNumber,
    Long quoteId,
    Long leadId,
    String ownerId,
    String currency,
    BigDecimal subtotal,
    BigDecimal taxRate,
    BigDecimal tax,
    BigDecimal total,
    String notes,
    CommercialOrderStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static CommercialOrderResponse fromEntity(CommercialOrder order) {
        return new CommercialOrderResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getQuoteId(),
            order.getLeadId(),
            order.getOwnerId(),
            order.getCurrency(),
            order.getSubtotal(),
            order.getTaxRate(),
            order.getTax(),
            order.getTotal(),
            order.getNotes(),
            order.getStatus(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
