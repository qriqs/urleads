package com.urleads.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import com.urleads.entity.Lead;

@Data
public class CreateLeadRequest {

    @NotBlank
    @Size(min = 2, max = 120)
    private String nombre;
    private String telefono;
    private String correo;
    private String etapa;
    private LocalDate proximoSeguimiento;
}