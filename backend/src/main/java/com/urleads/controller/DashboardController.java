package com.urleads.controller;

import com.urleads.config.OpenApiConfig;
import com.urleads.dto.DashboardResponse;
import com.urleads.dto.ErrorResponse;
import com.urleads.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Métricas agregadas y resumen de leads y seguimientos")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME)
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(
            summary = "Obtener métricas del dashboard",
            description = "Retorna el total de leads del usuario autenticado, desglose por etapa y resumen de seguimientos de hoy y atrasados."
    )
    @ApiResponse(responseCode = "200", description = "Métricas obtenidas exitosamente")
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboardMetrics() {
        return ResponseEntity.ok(dashboardService.obtenerMetricas());
    }
}