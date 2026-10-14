package com.urleads.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateLeadRequest {

    @NotBlank
    @Size(min = 2, max = 120)
    @JsonProperty("name")
    private String nombre;

    @JsonProperty("phone")
    private String telefono;

    @Email
    @JsonProperty("email")
    private String correo;

    @NotBlank
    @Pattern(regexp = "NUEVO|EN_SEGUIMIENTO|CERRADO")
    @JsonProperty("stage")
    private String etapa;

    @JsonProperty("nextFollowUp")
    private LocalDate proximoSeguimiento;

    @Size(max = 255)
    @JsonProperty("service")
    private String service;

    public void setNombre(String nombre) {
        this.nombre = (nombre == null) ? null : nombre.trim();
    }
}