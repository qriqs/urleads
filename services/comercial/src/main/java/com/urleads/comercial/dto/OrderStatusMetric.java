package com.urleads.comercial.dto;

import com.urleads.comercial.entity.CommercialOrderStatus;

import java.math.BigDecimal;

public record OrderStatusMetric(
    CommercialOrderStatus status,
    long count,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal total
) {}
