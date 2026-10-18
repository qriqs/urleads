package com.urleads.comercial.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CatalogItemRequest(
    @NotBlank(message = "El código no puede estar vacío")
    @Size(max = 50, message = "El código no debe superar los 50 caracteres")
    String code,

    @NotBlank(message = "El nombre no puede estar vacío")
    @Size(max = 255, message = "El nombre no debe superar los 255 caracteres")
    String name,

    String description,

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^(PEN|USD)$", message = "La moneda debe ser PEN o USD (ISO 4217)")
    String currency,

    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.01", message = "Debe ser mayor a 0")
    BigDecimal unitPrice,

    Boolean active
) {}
