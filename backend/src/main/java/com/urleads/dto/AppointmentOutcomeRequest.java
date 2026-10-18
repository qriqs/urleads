package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentOutcomeRequest {

    @JsonAlias({"minuta", "notes", "summary"})
    @Size(max = 10000, message = "El resumen no puede superar los 10000 caracteres")
    private String resumen;

    @JsonAlias({"agreements"})
    @Size(max = 5000, message = "Los acuerdos no pueden superar los 5000 caracteres")
    private String acuerdos;

    @JsonAlias({"proximosPasos", "nextActions", "nextSteps"})
    @Size(max = 5000, message = "Las próximas acciones no pueden superar los 5000 caracteres")
    private String proximasAcciones;

    @JsonAlias({"status", "outcome"})
    @Size(max = 64, message = "El resultado no puede superar los 64 caracteres")
    private String resultado;

    @Size(max = 64, message = "La asistencia no puede superar los 64 caracteres")
    private String asistencia;

    @Builder.Default
    private Boolean actualizarEstadoCita = true;
}
