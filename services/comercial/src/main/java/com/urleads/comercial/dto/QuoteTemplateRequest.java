package com.urleads.comercial.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record QuoteTemplateRequest(
    @NotBlank(message = "El nombre de la plantilla es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    String name,

    String description,

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "PEN|USD", message = "La moneda debe ser PEN o USD (ISO 4217)")
    String currency,

    @DecimalMin(value = "0.0000", message = "La tasa de impuesto no puede ser negativa")
    @DecimalMax(value = "1.0000", message = "La tasa de impuesto no puede ser mayor a 1.0000 (100%)")
    BigDecimal taxRate,

    String notes,

    List<QuoteItemRequest> items
) {}
