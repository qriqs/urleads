package com.urleads.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de confirmación transaccional e idempotente de importación de leads")
public record ImportConfirmResponse(
    @Schema(description = "Identificador de vista previa confirmado", example = "eyJhbGciOiJIUzI1NiJ9...")
    String previewId,

    @Schema(description = "Cantidad total de leads creados exitosamente", example = "10")
    int importedCount,

    @Schema(description = "Lista ordenada de IDs de los leads generados", example = "[101, 102, 103]")
    List<Long> leadIds
) {}
