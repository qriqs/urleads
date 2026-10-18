package com.urleads.controller;

import com.urleads.config.OpenApiConfig;
import com.urleads.dto.NotaRequest;
import com.urleads.dto.NotaResponse;
import com.urleads.service.NotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notas de Leads", description = "Bitácora interna y notas de seguimiento cronológicas del lead")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME)
@RestController
@RequestMapping("/api/leads/{id}/notes")
public class NotaController {

    private final NotaService notaService;

    public NotaController(NotaService notaService) {
        this.notaService = notaService;
    }

    @Operation(
            summary = "Listar notas de un lead",
            description = "Devuelve la bitácora cronológica interna del lead especificado, si pertenece al usuario autenticado. Requiere sesión web activa (cookie JSESSIONID)."
    )
    @ApiResponse(responseCode = "200", description = "Lista de notas obtenida exitosamente")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = com.urleads.dto.ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Lead no encontrado o pertenece a otro usuario", content = @Content(schema = @Schema(implementation = com.urleads.exception.ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<List<NotaResponse>> listarNotas(
            @Parameter(description = "ID del lead") @PathVariable("id") Long leadId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<NotaResponse> notas = notaService.listarNotas(leadId, userDetails.getUsername());
        return ResponseEntity.ok(notas);
    }

    @Operation(
            summary = "Crear nota en el lead",
            description = "Agrega una nueva nota a la bitácora interna del lead. Requiere sesión web activa (cookie JSESSIONID) y cabecera obligatoria X-XSRF-TOKEN con el token CSRF.",
            parameters = {
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN")
            }
    )
    @ApiResponse(responseCode = "201", description = "Nota creada exitosamente")
    @ApiResponse(responseCode = "400", description = "Contenido de la nota inválido o vacío")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = com.urleads.dto.ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Lead no encontrado o pertenece a otro usuario", content = @Content(schema = @Schema(implementation = com.urleads.exception.ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<NotaResponse> crearNota(
            @Parameter(description = "ID del lead") @PathVariable("id") Long leadId,
            @Valid @RequestBody NotaRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        NotaResponse creada = notaService.crearNota(leadId, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }
}