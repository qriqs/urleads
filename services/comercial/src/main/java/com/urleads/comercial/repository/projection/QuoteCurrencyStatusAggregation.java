package com.urleads.comercial.repository.projection;

import com.urleads.comercial.entity.QuoteStatus;

import java.math.BigDecimal;

public interface QuoteCurrencyStatusAggregation {
    String getCurrency();
    QuoteStatus getStatus();
    Long getCount();
    BigDecimal getSubtotal();
    BigDecimal getTax();
    BigDecimal getTotal();
}
