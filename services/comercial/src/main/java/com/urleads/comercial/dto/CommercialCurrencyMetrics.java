package com.urleads.comercial.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CommercialCurrencyMetrics(
    String currency,
    long totalQuotes,
    BigDecimal totalQuotesSubtotal,
    BigDecimal totalQuotesTax,
    BigDecimal totalQuotesAmount,
    long acceptedQuotes,
    BigDecimal acceptedQuotesAmount,
    BigDecimal quoteConversionRatePercentage,
    Map<String, QuoteStatusMetric> quotesByStatus,
    long totalOrders,
    BigDecimal totalOrdersSubtotal,
    BigDecimal totalOrdersTax,
    BigDecimal totalOrdersAmount,
    Map<String, OrderStatusMetric> ordersByStatus
) {}
