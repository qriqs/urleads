package com.urleads.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.urleads.config.OpenApiConfig;
import com.urleads.dto.ErrorResponse;
import com.urleads.dto.ImportConfirmResponse;
import com.urleads.dto.ImportPreviewResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.LeadImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Importación de Leads",
        description = "Endpoints acotados para vista previa y confirmación transaccional e idempotente de leads desde archivos Excel (.xlsx) con plantilla fija"
)
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME)
@RestController
@RequestMapping("/api/leads/import")
@RequiredArgsConstructor
public class LeadImportController {

    private final LeadImportService leadImportService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(
            summary = "Vista previa de importación de leads desde Excel",
            description = "Valida de forma acotada y sin mutaciones un archivo .xlsx contra la plantilla fija de 8 columnas. " +
                    "Retorna el diagnóstico fila por fila (errores y duplicados intra-archivo y contra base de datos del usuario), " +
                    "y emite un previewId criptográfico con validez de 24 horas si el archivo es apto para confirmar. " +
                    "Requiere sesión web activa y cabecera obligatoria X-XSRF-TOKEN.",
            parameters = {
                    @Parameter(
                            name = "X-XSRF-TOKEN",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN"
                    )
            }
    )
    @ApiResponse(responseCode = "200", description = "Vista previa generada exitosamente")
    @ApiResponse(responseCode = "400", description = "Archivo inválido, corrupto, cabeceras incorrectas o supera límites",
            content = @Content(schema = @Schema(implementation = com.urleads.exception.ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin sesión activa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token CSRF ausente o inválido")
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreviewResponse preview(
            @Parameter(description = "Libro de Excel en formato .xlsx (máximo 5 MiB y 500 filas de datos)", required = true)
            @RequestParam("file") MultipartFile file) {

        Long usuarioId = currentUserProvider.getCurrentUserId();
        return leadImportService.preview(usuarioId, file);
    }

    @Operation(
            summary = "Confirmar importación transaccional e idempotente de leads",
            description = "Inserta de manera atómica todos los leads válidos del libro .xlsx re-enviado, verificando el previewId, " +
                    "la firma criptográfica, la vigencia (24h) y el resumen SHA-256 idéntico del archivo. " +
                    "Si cualquier fila falla o existe algún duplicado, se aborta la transacción completa y se insertan cero leads. " +
                    "Reintentos con el mismo token y contenido retornan exactamente el mismo resultado registrado en el recibo durable. " +
                    "Requiere sesión web activa y cabecera obligatoria X-XSRF-TOKEN.",
            parameters = {
                    @Parameter(
                            name = "X-XSRF-TOKEN",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN"
                    )
            }
    )
    @ApiResponse(responseCode = "200", description = "Importación confirmada exitosamente o recibo idempotente retornado")
    @ApiResponse(responseCode = "400", description = "Validación fallida, token expirado, archivo modificado o filas con error",
            content = @Content(schema = @Schema(implementation = com.urleads.exception.ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin sesión activa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token CSRF ausente o inválido")
    @ApiResponse(responseCode = "404", description = "Vista previa no encontrada o no pertenece al usuario",
            content = @Content(schema = @Schema(implementation = com.urleads.exception.ErrorResponse.class)))
    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportConfirmResponse confirm(
            @Parameter(description = "Libro de Excel idéntico al verificado en la vista previa", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Identificador opaco de vista previa recibido en la llamada de preview", required = true)
            @RequestParam("previewId") String previewId) {

        Long usuarioId = currentUserProvider.getCurrentUserId();
        return leadImportService.confirm(usuarioId, file, previewId);
    }
}
