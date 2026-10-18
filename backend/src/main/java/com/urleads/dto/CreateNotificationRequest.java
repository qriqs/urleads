package com.urleads.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {

    @Size(max = 64)
    private String eventId;

    @NotBlank(message = "El tipo de notificación es obligatorio")
    @Size(max = 64)
    private String tipo;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 255)
    private String titulo;

    @NotBlank(message = "El mensaje es obligatorio")
    private String mensaje;

    private String data;
}
