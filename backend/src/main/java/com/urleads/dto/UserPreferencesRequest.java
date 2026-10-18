package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesRequest {

    @Size(max = 64, message = "La zona horaria no puede superar 64 caracteres")
    private String zonaHoraria;

    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime inicioJornada;

    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime finJornada;

    private String diasLaborales;

    private Boolean notificacionesHabilitadas;

    private Boolean canalWebsocket;

    private Boolean canalEmail;

    private List<String> canalesHabilitados;
}
