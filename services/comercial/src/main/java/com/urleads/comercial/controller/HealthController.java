package com.urleads.comercial.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "urleads-comercial",
            "port", 8081
        ));
    }

    @GetMapping("/secure-ping")
    public ResponseEntity<Map<String, Object>> securePing(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
            "message", "pong",
            "ownerId", jwt.getSubject(),
            "scope", jwt.getClaimAsString("scope")
        ));
    }
}
