package com.urleads.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotaRequest(
    @NotBlank(message = "El contenido no puede estar vacío")
    @Size(min = 1, max = 2000, message = "El contenido debe tener entre 1 y 2000 caracteres")
    String content
) {
    public NotaRequest {
        if (content != null) {
            content = content.trim();
        }
    }
}