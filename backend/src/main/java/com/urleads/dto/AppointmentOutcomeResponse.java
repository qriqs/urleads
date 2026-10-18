package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urleads.entity.AgendaCitaMinuta;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentOutcomeResponse {

    private Long id;
    private Long citaId;
    private Long usuarioId;
    private String resumen;
    private String minuta;
    private String acuerdos;
    private String proximasAcciones;
    private String resultado;
    private String asistencia;
    private String estadoCita;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant creadoEn;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant actualizadoEn;

    public static AppointmentOutcomeResponse fromEntity(AgendaCitaMinuta entity) {
        if (entity == null) return null;
        return AppointmentOutcomeResponse.builder()
                .id(entity.getId())
                .citaId(entity.getCita() != null ? entity.getCita().getId() : null)
                .usuarioId(entity.getUsuario() != null ? entity.getUsuario().getId() : null)
                .resumen(entity.getResumen())
                .minuta(entity.getResumen())
                .acuerdos(entity.getAcuerdos())
                .proximasAcciones(entity.getProximasAcciones())
                .resultado(entity.getResultado())
                .asistencia(entity.getAsistencia())
                .estadoCita(entity.getCita() != null ? entity.getCita().getEstado() : null)
                .creadoEn(entity.getCreadoEn())
                .actualizadoEn(entity.getActualizadoEn())
                .build();
    }
}
