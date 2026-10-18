package com.urleads.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnoozeNotificationRequest {

    private static final Map<String, Integer> PRESETS = Map.of(
            "15m", 15,
            "1h", 60,
            "1d", 1440
    );

    /**
     * Preset preestablecido de aplazamiento: '15m' (15 minutos), '1h' (1 hora), '1d' (1 día / 24 horas).
     */
    private String preset;

    /**
     * Minutos explícitos a posponer (mínimo 1, máximo 10080 = 7 días). Opcional si se especifica preset.
     */
    @Min(value = 1, message = "Los minutos a posponer deben ser al menos 1")
    @Max(value = 10080, message = "No se puede posponer más de 7 días (10080 minutos)")
    private Integer minutos;

    /**
     * Resuelve los minutos a posponer considerando los presets acordados ('15m', '1h', '1d').
     */
    public int resolverMinutos() {
        if (preset != null && !preset.isBlank()) {
            String normalizado = preset.trim().toLowerCase();
            Integer minutosPreset = PRESETS.get(normalizado);
            if (minutosPreset == null) {
                throw new IllegalArgumentException("Preset de aplazamiento inválido: '" + preset + "'. Opciones permitidas: '15m', '1h', '1d'");
            }
            return minutosPreset;
        }

        if (minutos != null && minutos > 0) {
            return minutos;
        }

        // Por defecto: 15 minutos (15m)
        return 15;
    }
}
