package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaItemResponse {

    private String tipo; // "TAREA" o "CITA"
    private Long id;
    private String referencia; // "TAREA-1" o "CITA-2"
    private Long leadId;
    private String titulo;
    private String descripcion;
    private LocalDate fecha;
    private LocalTime hora;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant momentoUtc;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant finUtc;

    private String estado;
    private TaskResponse tarea;
    private AppointmentResponse cita;
}
