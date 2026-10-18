package com.urleads.comercial.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InstantiateQuoteRequest(
    @NotNull(message = "El identificador de lead es obligatorio")
    Long leadId,

    String notes,

    @DecimalMin(value = "0.0000", message = "La tasa de impuesto no puede ser negativa")
    @DecimalMax(value = "1.0000", message = "La tasa de impuesto no puede ser mayor a 1.0000 (100%)")
    BigDecimal taxRate
) {}
