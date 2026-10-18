package com.urleads.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {

    @NotBlank(message = "El estado es obligatorio")
    private String estado;
}
