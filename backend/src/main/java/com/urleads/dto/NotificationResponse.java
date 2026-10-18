package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urleads.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String eventId;
    private String tipo;
    private String titulo;
    private String mensaje;
    private String data;
    private boolean leida;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant leidaEn;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant pospuestaHasta;

    private int snoozeCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant creadoEn;

    public static NotificationResponse fromEntity(Notification entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .tipo(entity.getTipo())
                .titulo(entity.getTitulo())
                .mensaje(entity.getMensaje())
                .data(entity.getData())
                .leida(entity.isLeida())
                .leidaEn(entity.getLeidaEn())
                .pospuestaHasta(entity.getPospuestaHasta())
                .snoozeCount(entity.getSnoozeCount())
                .creadoEn(entity.getCreadoEn())
                .build();
    }
}
