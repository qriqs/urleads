package com.urleads.comercial.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record QuoteItemRequest(
    Long catalogItemId,
    String code,
    String name,
    String description,

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a 0")
    BigDecimal quantity,

    @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a 0")
    BigDecimal unitPrice
) {}
