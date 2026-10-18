package com.urleads.comercial.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record QuoteUpdateRequest(
    @DecimalMin(value = "0.00", message = "La tasa de impuesto no puede ser negativa")
    BigDecimal taxRate,

    String notes
) {}
