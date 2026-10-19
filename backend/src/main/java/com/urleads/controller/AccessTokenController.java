package com.urleads.controller;

import com.urleads.config.OpenApiConfig;
import com.urleads.dto.AccessTokenResponse;
import com.urleads.dto.ErrorResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtIssuanceService;
import com.urleads.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Tokens de Acceso", description = "Emisión de tokens JWT para servicios internos (Backend V2 C02)")
@RestController
@RequestMapping("/api/auth")
public class AccessTokenController {

    private final UsuarioRepository usuarioRepository;
    private final Optional<JwtIssuanceService> jwtIssuanceServiceOptional;
    private final JwtService jwtService;

    public AccessTokenController(
            UsuarioRepository usuarioRepository,
            @Autowired(required = false) JwtIssuanceService jwtIssuanceService,
            @Autowired(required = false) JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.jwtIssuanceServiceOptional = Optional.ofNullable(jwtIssuanceService);
        this.jwtService = jwtService;
    }

    @Operation(
            summary = "Emitir token de acceso JWT",
            description = "Emite un token Bearer JWT RS256 con audiencia crm-api y scope crm.leads.read. Requiere sesión web activa (cookie JSESSIONID) y cabecera obligatoria X-XSRF-TOKEN con el token CSRF.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME),
            parameters = {
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN")
            }
    )
    @ApiResponse(responseCode = "200", description = "Token emitido exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccessTokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin sesión activa o usuario no encontrado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Servicio de emisión de tokens no configurado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/access-token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> issueAccessToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("Sin sesión activa"));
        }

        String username = authentication.getName();
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("Usuario no encontrado"));
        }

        if (jwtIssuanceServiceOptional.isEmpty() || !jwtIssuanceServiceOptional.get().isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("Servicio de emisión de tokens no configurado"));
        }

        AccessTokenResponse tokenResponse = jwtIssuanceServiceOptional.get().issueAccessToken(usuario.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(tokenResponse);
    }
}
