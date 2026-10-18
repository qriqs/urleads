package com.urleads.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.urleads.config.OpenApiConfig;
import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.exception.ErrorResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.LeadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Leads", description = "Operaciones CRUD y filtros de leads del usuario autenticado")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME)
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(
            summary = "Listar leads paginados con filtros",
            description = "Retorna la lista paginada de leads pertenecientes al usuario autenticado, permitiendo filtrar por texto libre, estado de seguimiento y etapa. Requiere sesión web activa (cookie JSESSIONID)."
    )
    @ApiResponse(responseCode = "200", description = "Página de leads obtenida exitosamente")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = com.urleads.dto.ErrorResponse.class)))
    @GetMapping
    public PageResponse<LeadResponse> listar(
            @Parameter(description = "Búsqueda textual en nombre, teléfono o correo") @RequestParam(required = false) String search,
            @Parameter(description = "Filtro de seguimiento: 'today', 'overdue' o 'all'") @RequestParam(required = false, defaultValue = "all") String followUp,
            @Parameter(description = "Filtro por etapa: 'NUEVO', 'EN_SEGUIMIENTO', 'CERRADO'") @RequestParam(required = false) String stage,
            @Parameter(description = "Número de página (base 0)") @RequestParam(required = false, defaultValue = "0") int page) {

        return leadService.listarPaginado(
                currentUserProvider.getCurrentUserId(),
                search,
                followUp,
                stage,
                page);
    }

    @Operation(
            summary = "Obtener lead por ID",
            description = "Retorna el detalle completo del lead si pertenece al usuario autenticado. Requiere sesión web activa (cookie JSESSIONID). Retorna 404 si no existe o si pertenece a otro usuario."
    )
    @ApiResponse(responseCode = "200", description = "Lead obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = com.urleads.dto.ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Lead no encontrado o pertenece a otro usuario", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public LeadResponse obtener(@Parameter(description = "ID del lead") @PathVariable Long id) {

        return leadService.obtener(
                id,
                currentUserProvider.getCurrentUserId());
    }

    @Operation(
            summary = "Crear un nuevo lead",
            description = "Crea un lead asignado al usuario autenticado. Requiere sesión web activa (cookie JSESSIONID) y cabecera obligatoria X-XSRF-TOKEN con el token CSRF.",
            parameters = {
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN")
            }
    )
    @ApiResponse(responseCode = "201", description = "Lead creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Validación fallida")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = com.urleads.dto.ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse crear(@Valid @RequestBody CreateLeadRequest request) {

        return leadService.crear(
                currentUserProvider.getCurrentUserId(),
                request);
    }

    @Operation(
            summary = "Actualizar lead existente",
            description = "Actualiza los campos de un lead existente si pertenece al usuario autenticado. Requiere sesión web activa (cookie JSESSIONID) y cabecera obligatoria X-XSRF-TOKEN con el token CSRF.",
            parameters = {
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN")
            }
    )
    @ApiResponse(responseCode = "200", description = "Lead actualizado exitosamente")
    @ApiResponse(responseCode = "400", description = "Validación fallida")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = com.urleads.dto.ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Lead no encontrado o pertenece a otro usuario", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    public LeadResponse actualizar(
            @Parameter(description = "ID del lead a actualizar") @PathVariable Long id,
            @Valid @RequestBody UpdateLeadRequest request) {

        return leadService.actualizar(
                id,
                currentUserProvider.getCurrentUserId(),
                request);
    }

    @Operation(
            summary = "Eliminar lead",
            description = "Elimina un lead por ID si pertenece al usuario autenticado. Requiere sesión web activa (cookie JSESSIONID) y cabecera obligatoria X-XSRF-TOKEN con el token CSRF.",
            parameters = {
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN")
            }
    )
    @ApiResponse(responseCode = "204", description = "Lead eliminado exitosamente")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = com.urleads.dto.ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Lead no encontrado o pertenece a otro usuario", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@Parameter(description = "ID del lead a eliminar") @PathVariable Long id) {

        leadService.eliminar(
                id,
                currentUserProvider.getCurrentUserId());
    }
}