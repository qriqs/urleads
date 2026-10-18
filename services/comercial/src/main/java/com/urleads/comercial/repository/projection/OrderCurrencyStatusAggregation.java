package com.urleads.comercial.repository.projection;

import com.urleads.comercial.entity.CommercialOrderStatus;

import java.math.BigDecimal;

public interface OrderCurrencyStatusAggregation {
    String getCurrency();
    CommercialOrderStatus getStatus();
    Long getCount();
    BigDecimal getSubtotal();
    BigDecimal getTax();
    BigDecimal getTotal();
}
