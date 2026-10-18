package com.urleads.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Salud", description = "Verificación de disponibilidad operativa del servicio CRM")
@RestController
@RequestMapping("/api")
public class HealthController {

    public record HealthResponse(String status) {
    }

    @Operation(summary = "Verificar estado de salud", description = "Endpoint público para verificar que el servicio está activo.")
    @ApiResponse(responseCode = "200", description = "Servicio activo y saludable")
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP"));
    }
}
