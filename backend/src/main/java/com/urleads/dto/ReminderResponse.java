package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urleads.entity.Recordatorio;
import lombok.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderResponse {

    private Long id;
    private String occurrenceId;
    private Long usuarioId;
    private String targetType;
    private Long targetId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant programadoPara;

    private String programadoParaLocal;
    private String estado;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant creadoEn;

    public static ReminderResponse fromEntity(Recordatorio r) {
        if (r == null) return null;

        ZoneId limaZone = ZoneId.of("America/Lima");
        String localStr = r.getProgramadoPara() != null
                ? r.getProgramadoPara().atZone(limaZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : null;

        return ReminderResponse.builder()
                .id(r.getId())
                .occurrenceId(r.getOccurrenceId())
                .usuarioId(r.getUsuario() != null ? r.getUsuario().getId() : null)
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .programadoPara(r.getProgramadoPara())
                .programadoParaLocal(localStr)
                .estado(r.getEstado())
                .creadoEn(r.getCreadoEn())
                .build();
    }
}
