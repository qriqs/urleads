package com.urleads.comercial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

public record QuoteCreateRequest(
    @NotNull(message = "El identificador del lead es obligatorio")
    Long leadId,

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^(PEN|USD)$", message = "La moneda debe ser PEN o USD (ISO 4217)")
    String currency,

    @DecimalMin(value = "0.00", message = "La tasa de impuesto no puede ser negativa")
    BigDecimal taxRate,

    String notes,

    @Valid
    List<QuoteItemRequest> items
) {}
