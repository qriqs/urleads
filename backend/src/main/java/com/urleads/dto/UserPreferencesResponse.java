package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urleads.entity.UserPreferences;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesResponse {

    private Long id;
    private Long usuarioId;
    private String zonaHoraria;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime inicioJornada;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime finJornada;

    private String diasLaborales;
    private Boolean notificacionesHabilitadas;
    private Boolean canalWebsocket;
    private Boolean canalEmail;
    private List<String> canalesHabilitados;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant creadoEn;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant actualizadoEn;

    public static UserPreferencesResponse fromEntity(UserPreferences entity) {
        if (entity == null) return null;

        List<String> canales = new ArrayList<>();
        if (Boolean.TRUE.equals(entity.getCanalWebsocket())) {
            canales.add("WEBSOCKET");
        }
        if (Boolean.TRUE.equals(entity.getCanalEmail())) {
            canales.add("EMAIL");
        }

        return UserPreferencesResponse.builder()
                .id(entity.getId())
                .usuarioId(entity.getUsuario() != null ? entity.getUsuario().getId() : null)
                .zonaHoraria(entity.getZonaHoraria())
                .inicioJornada(entity.getInicioJornada())
                .finJornada(entity.getFinJornada())
                .diasLaborales(entity.getDiasLaborales())
                .notificacionesHabilitadas(entity.getNotificacionesHabilitadas())
                .canalWebsocket(entity.getCanalWebsocket())
                .canalEmail(entity.getCanalEmail())
                .canalesHabilitados(canales)
                .creadoEn(entity.getCreadoEn())
                .actualizadoEn(entity.getActualizadoEn())
                .build();
    }
}
