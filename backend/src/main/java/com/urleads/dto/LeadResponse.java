package com.urleads.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @JsonProperty("tags")
    private List<TagResponse> tags = new ArrayList<>();

    public LeadResponse(Long id, String nombre, String telefono, String correo, String etapa,
                        LocalDate proximoSeguimiento, OffsetDateTime creadoEn, OffsetDateTime actualizadoEn,
                        String service) {
        this(id, nombre, telefono, correo, etapa, proximoSeguimiento, creadoEn, actualizadoEn, service, new ArrayList<>());
    }
}
