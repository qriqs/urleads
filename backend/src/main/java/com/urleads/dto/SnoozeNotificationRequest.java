package com.urleads.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnoozeNotificationRequest {

    @Min(value = 1, message = "Los minutos a posponer deben ser al menos 1")
    @Max(value = 10080, message = "No se puede posponer más de 7 días (10080 minutos)")
    private int minutos;
}
