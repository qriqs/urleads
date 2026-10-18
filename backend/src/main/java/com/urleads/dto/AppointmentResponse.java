package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urleads.entity.AgendaCita;
import lombok.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {

    private Long id;
    private Long usuarioId;
    private Long leadId;
    private String titulo;
    private String descripcion;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant inicio;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant fin;

    private String zonaHoraria;
    private String inicioLocal;
    private String finLocal;
    private String estado;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant creadoEn;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant actualizadoEn;

    public static AppointmentResponse fromEntity(AgendaCita cita) {
        if (cita == null) return null;

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(cita.getZonaHoraria() != null && !cita.getZonaHoraria().isBlank()
                    ? cita.getZonaHoraria() : "America/Lima");
        } catch (Exception ex) {
            zoneId = ZoneId.of("America/Lima");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        String inicioLocal = cita.getInicio() != null ? cita.getInicio().atZone(zoneId).format(formatter) : null;
        String finLocal = cita.getFin() != null ? cita.getFin().atZone(zoneId).format(formatter) : null;

        return AppointmentResponse.builder()
                .id(cita.getId())
                .usuarioId(cita.getUsuario() != null ? cita.getUsuario().getId() : null)
                .leadId(cita.getLeadId())
                .titulo(cita.getTitulo())
                .descripcion(cita.getDescripcion())
                .inicio(cita.getInicio())
                .fin(cita.getFin())
                .zonaHoraria(cita.getZonaHoraria())
                .inicioLocal(inicioLocal)
                .finLocal(finLocal)
                .estado(cita.getEstado())
                .creadoEn(cita.getCreadoEn())
                .actualizadoEn(cita.getActualizadoEn())
                .build();
    }
}
