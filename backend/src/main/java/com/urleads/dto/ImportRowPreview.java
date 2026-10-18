package com.urleads.dto;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Detalle y estado de validación de una fila del archivo Excel importado")
public record ImportRowPreview(
    @Schema(description = "Número de fila en Excel (base 1, encabezado es 1)", example = "2")
    int rowNumber,

    @Schema(description = "Estado de la fila: VALID, INVALID o DUPLICATE", example = "VALID")
    String status,

    @Schema(description = "Valores extraídos de los campos mapeados")
    Map<String, String> values,

    @Schema(description = "Lista de errores específicos de validación para esta fila")
    List<String> errors
) {}
