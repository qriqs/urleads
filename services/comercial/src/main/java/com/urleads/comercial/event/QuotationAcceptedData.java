package com.urleads.comercial.event;

import java.math.BigDecimal;

public record QuotationAcceptedData(
    Long quoteId,
    Long leadId,
    String currency,
    BigDecimal total
) {}
