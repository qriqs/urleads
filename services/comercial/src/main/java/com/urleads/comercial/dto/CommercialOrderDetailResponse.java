package com.urleads.comercial.dto;

import com.urleads.comercial.entity.CommercialOrder;
import com.urleads.comercial.entity.CommercialOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CommercialOrderDetailResponse(
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
    List<CommercialOrderItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
    public static CommercialOrderDetailResponse fromEntity(CommercialOrder order) {
        List<CommercialOrderItemResponse> itemResponses = order.getItems() != null
            ? order.getItems().stream().map(CommercialOrderItemResponse::fromEntity).toList()
            : List.of();

        return new CommercialOrderDetailResponse(
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
            itemResponses,
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
