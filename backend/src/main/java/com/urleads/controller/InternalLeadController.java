package com.urleads.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urleads.config.OpenApiConfig;
import com.urleads.dto.InternalLeadResponse;
import com.urleads.exception.ErrorResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.LeadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST para la consulta interna de leads entre microservicios (Backend V2 C03).
 * Protegido bajo /internal/** con autenticación Bearer JWT RS256, audiencia crm-api
 * y scope crm.leads.read. Resuelve el propietario exclusivamente a través de CurrentUserProvider.
 */
@Tag(name = "Consulta Interna", description = "Consulta interna de leads entre microservicios (Backend V2 C03)")
@SecurityRequirement(name = OpenApiConfig.BEARER_JWT_SCHEME)
@RestController
@RequestMapping("/internal/v1/leads")
public class InternalLeadController {

    private final LeadService leadService;
    private final CurrentUserProvider currentUserProvider;

    public InternalLeadController(LeadService leadService, CurrentUserProvider currentUserProvider) {
        this.leadService = leadService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Consultar lead interno",
            description = "Consulta interna sin datos personales (PII). Requiere Bearer JWT RS256 con audiencia crm-api y scope crm.leads.read. El propietario se deriva exclusivamente del claim sub del JWT. Retorna únicamente id, stage y nextFollowUp. Retorna 404 indistinguible si el lead no existe o pertenece a otro usuario."
    )
    @ApiResponse(responseCode = "200", description = "Lead interno encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalLeadResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o no firmado por clave autorizada")
    @ApiResponse(responseCode = "403", description = "Token JWT sin el scope requerido crm.leads.read")
    @ApiResponse(responseCode = "404", description = "Lead no encontrado o no pertenece al propietario identificado en sub", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public InternalLeadResponse obtenerInterno(@Parameter(description = "ID del lead a consultar") @PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return leadService.obtenerInterno(id, usuarioId);
    }
}
