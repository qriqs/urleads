package com.urleads.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de la vista previa de importación de leads desde Excel")
public record ImportPreviewResponse(
    @Schema(description = "Identificador opaco de vista previa vinculado al propietario y al contenido, válido por 24 horas", example = "eyJhbGciOiJIUzI1NiJ9...")
    String previewId,

    @Schema(description = "Total de filas de datos procesadas (excluyendo cabecera y filas totalmente vacías)", example = "10")
    int totalRows,

    @Schema(description = "Número de filas válidas sin errores", example = "10")
    int validRows,

    @Schema(description = "Número de filas inválidas o duplicadas", example = "0")
    int invalidRows,

    @Schema(description = "Indica si el archivo completo es confirmable (todas las filas válidas y al menos una fila)", example = "true")
    boolean canConfirm,

    @Schema(description = "Detalle ordenado fila por fila con número de fila original y errores detectados")
    List<ImportRowPreview> rows
) {}
