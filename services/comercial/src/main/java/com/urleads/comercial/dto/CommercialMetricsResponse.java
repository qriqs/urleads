package com.urleads.comercial.dto;

import java.util.List;

public record CommercialMetricsResponse(
    String ownerId,
    List<CommercialCurrencyMetrics> metricsByCurrency
) {}
