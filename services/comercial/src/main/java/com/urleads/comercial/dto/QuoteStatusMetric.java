package com.urleads.comercial.dto;

import com.urleads.comercial.entity.QuoteStatus;

import java.math.BigDecimal;

public record QuoteStatusMetric(
    QuoteStatus status,
    long count,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal total
) {}
