package com.urleads.dto;

import java.time.LocalDate;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;


@Data
public class CreateLeadRequest {

    @NotBlank
    @Size(min = 2, max = 120)
    @JsonProperty("name")
    private String nombre;

    @JsonProperty("phone")
    private String telefono;
    @Email
    @JsonProperty("email")
    private String correo;

    @JsonProperty("stage")
    private String etapa;

    @JsonProperty("nextFollowUp")
    private LocalDate proximoSeguimiento;

    @JsonProperty("service")
    private String service;

}