package com.urleads.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentRequest {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 255, message = "El título no puede superar los 255 caracteres")
    private String titulo;

    private String descripcion;

    private Long leadId;

    @NotBlank(message = "La fecha/hora de inicio es obligatoria")
    private String inicio;

    @NotBlank(message = "La fecha/hora de fin es obligatoria")
    private String fin;

    private String zonaHoraria;

    private String estado;
}
