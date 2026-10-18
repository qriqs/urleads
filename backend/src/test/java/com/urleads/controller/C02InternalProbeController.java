package com.urleads.controller;

import com.urleads.security.CurrentUserProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador de prueba exclusivo para la verificación de seguridad de rutas internas
 * {@code /internal/**} bajo autenticación JWT RS256 (Tarea C02).
 * NO forma parte del código de producción y no sustituye la implementación futura de C03.
 */
@RestController
@RequestMapping("/internal/v1/c02-probe")
public class C02InternalProbeController {

    private final CurrentUserProvider currentUserProvider;

    public C02InternalProbeController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> probe(
            @RequestParam(name = "ownerId", required = false) String ignoredQueryOwnerId) {
        Long ownerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(Map.of(
                "ownerId", ownerId,
                "status", "OK"
        ));
    }
}
