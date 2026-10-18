package com.urleads.dto;

import com.urleads.entity.AgendaTarea;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private Long leadId;
    private String titulo;
    private String descripcion;
    private LocalDate fechaLimite;
    private LocalTime horaLimite;
    private String estado;
    private Instant creadoEn;
    private Instant actualizadoEn;

    public static TaskResponse fromEntity(AgendaTarea tarea) {
        if (tarea == null) return null;
        return TaskResponse.builder()
                .id(tarea.getId())
                .leadId(tarea.getLeadId())
                .titulo(tarea.getTitulo())
                .descripcion(tarea.getDescripcion())
                .fechaLimite(tarea.getFechaLimite())
                .horaLimite(tarea.getHoraLimite())
                .estado(tarea.getEstado())
                .creadoEn(tarea.getCreadoEn())
                .actualizadoEn(tarea.getActualizadoEn())
                .build();
    }
}
