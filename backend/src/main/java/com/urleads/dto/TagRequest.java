package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
    @NotBlank(message = "El nombre de la etiqueta no puede estar vacío")
    @Size(min = 1, max = 100, message = "El nombre de la etiqueta debe tener entre 1 y 100 caracteres")
    @JsonProperty("name")
    String name
) {
    public TagRequest {
        if (name != null) {
            name = name.trim();
        }
    }
}
