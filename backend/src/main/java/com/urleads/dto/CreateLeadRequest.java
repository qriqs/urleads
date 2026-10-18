package com.urleads.dto;

import java.time.LocalDate;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    @NotBlank
    @Pattern(regexp = "NUEVO|EN_SEGUIMIENTO|CERRADO")
    @JsonProperty("stage")
    private String etapa;

    @JsonProperty("nextFollowUp")
    private LocalDate proximoSeguimiento;

    @Size(max = 255)
    @JsonProperty("service")
    private String service;

    @Pattern(regexp = "BAJA|MEDIA|ALTA", message = "Prioridad no válida: solo se permite BAJA, MEDIA o ALTA")
    @JsonProperty("priority")
    private String priority;

    @Pattern(regexp = "WEB|REFERIDO|MANUAL", message = "Origen no válido: solo se permite WEB, REFERIDO o MANUAL")
    @JsonProperty("origin")
    private String origin;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean prioritySupplied;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean originSupplied;

    // Recorta antes de validar: " a " queda "a" y falla @Size(min = 2)
    public void setNombre(String nombre) {
        this.nombre = (nombre == null) ? null : nombre.trim();
    }

    public void setTelefono(String telefono) {
        this.telefono = (telefono == null || telefono.isBlank()) ? null : telefono.trim();
    }

    public void setCorreo(String correo) {
        this.correo = (correo == null || correo.isBlank()) ? null : correo.trim();
    }

    public void setService(String service) {
        this.service = (service == null || service.isBlank()) ? null : service.trim();
    }

    public void setPriority(String priority) {
        this.prioritySupplied = true;
        if (priority == null) {
            this.priority = null;
        } else if (priority.isBlank()) {
            this.priority = "";
        } else {
            this.priority = priority.trim().toUpperCase(Locale.ROOT);
        }
    }

    public void setOrigin(String origin) {
        this.originSupplied = true;
        if (origin == null) {
            this.origin = null;
        } else if (origin.isBlank()) {
            this.origin = "";
        } else {
            this.origin = origin.trim().toUpperCase(Locale.ROOT);
        }
    }
}