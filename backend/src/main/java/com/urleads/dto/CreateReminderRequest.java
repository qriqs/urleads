package com.urleads.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReminderRequest {

    private String occurrenceId; // Opcional, generado automáticamente si no se envía

    @NotBlank(message = "El tipo de objetivo (targetType) es obligatorio ('CITA', 'TAREA', 'LEAD')")
    private String targetType;

    @NotNull(message = "El identificador del objetivo (targetId) es obligatorio")
    private Long targetId;

    /**
     * Timestamp exacto ISO-8601 en UTC o con offset (ej. '2026-10-25T14:45:00Z').
     */
    private String programadoPara;

    /**
     * Fecha de seguimiento en formato YYYY-MM-DD si se programa a nivel de día sin hora exacta.
     * Se convertirá a timestamp exacto a las 09:00:00 en la zona de Lima.
     */
    private String fechaSeguimiento;

    /**
     * Minutos de antelación respecto al evento (ej. 15 para 15 minutos antes).
     */
    private Integer antelacionMinutos;
}
