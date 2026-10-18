package com.urleads.controller;

import com.urleads.dto.AccessTokenResponse;
import com.urleads.dto.ErrorResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtIssuanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Controlador para la emisión de tokens de acceso JWT (Backend V2 C02).
 * Expone {@code POST /api/auth/access-token} protegido por la sesión web existente y CSRF.
 */
@RestController
@RequestMapping("/api/auth")
public class AccessTokenController {

    private final UsuarioRepository usuarioRepository;
    private final Optional<JwtIssuanceService> jwtIssuanceServiceOptional;

    public AccessTokenController(
            UsuarioRepository usuarioRepository,
            @Autowired(required = false) JwtIssuanceService jwtIssuanceService) {
        this.usuarioRepository = usuarioRepository;
        this.jwtIssuanceServiceOptional = Optional.ofNullable(jwtIssuanceService);
    }

    @PostMapping(value = "/access-token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> issueAccessToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("Sin sesión activa"));
        }

        if (jwtIssuanceServiceOptional.isEmpty() || !jwtIssuanceServiceOptional.get().isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("Servicio de emisión de tokens no configurado"));
        }

        String username = authentication.getName();
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("Usuario no encontrado"));
        }

        AccessTokenResponse tokenResponse = jwtIssuanceServiceOptional.get().issueAccessToken(usuario.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(tokenResponse);
    }
}
