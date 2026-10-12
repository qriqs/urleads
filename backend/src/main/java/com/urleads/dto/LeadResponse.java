package com.urleads.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadResponse {

    private Long id;

    private String nombre;

    private String telefono;

    private String correo;

    private String etapa;

    private LocalDate proximoSeguimiento;

    private OffsetDateTime creadoEn;

    private OffsetDateTime actualizadoEn;
}