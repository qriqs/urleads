package com.urleads.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPendingResponse {

    private LocalDate fecha;
    private String zonaHoraria;
    private int totalPendientes;
    private int totalTareasPendientes;
    private int totalCitasProgramadas;
    private List<TaskResponse> tareas;
    private List<AppointmentResponse> citas;
    private List<AgendaItemResponse> items;
}
