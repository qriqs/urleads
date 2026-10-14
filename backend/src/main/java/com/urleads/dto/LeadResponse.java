package com.urleads.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
//Y convertir los campos para complir con el contrato
import com.fasterxml.jackson.annotation.JsonProperty;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadResponse {

    private Long id;

    @JsonProperty("name")
    private String nombre;

    @JsonProperty("phone")
    private String telefono;

    @JsonProperty("email")
    private String correo;

    @JsonProperty("stage")
    private String etapa;

    @JsonProperty("nextFollowUp")
    private LocalDate proximoSeguimiento;

    @JsonProperty("createdAt")
    private OffsetDateTime creadoEn;

    @JsonProperty("updatedAt")
    private OffsetDateTime actualizadoEn;

    @JsonProperty("service")
    private String service;
}