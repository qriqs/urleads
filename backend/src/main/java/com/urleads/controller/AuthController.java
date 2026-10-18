package com.urleads.controller;

import com.urleads.config.OpenApiConfig;
import com.urleads.dto.ErrorResponse;
import com.urleads.dto.LoginRequest;
import com.urleads.dto.UserResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Autenticación", description = "Autenticación de usuario, gestión de sesión web y token CSRF")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
    }

    @Operation(
            summary = "Iniciar sesión",
            description = "Autentica al usuario mediante credenciales (username/password) y establece una cookie de sesión JSESSIONID HttpOnly. Endpoint público no autenticado que requiere cabecera obligatoria X-XSRF-TOKEN con el token CSRF obtenido de GET /api/auth/csrf.",
            parameters = {
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Token CSRF obtenido previamente de GET /api/auth/csrf")
            }
    )
    @ApiResponse(responseCode = "200", description = "Inicio de sesión exitoso", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                httpRequest.changeSessionId();
            } else {
                session = httpRequest.getSession(true);
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            Usuario usuario = usuarioRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

            return ResponseEntity.ok(new UserResponse(usuario.getId(), usuario.getUsername()));

        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Credenciales inválidas"));
        }
    }

    @Operation(summary = "Consultar usuario autenticado", description = "Obtiene los datos del usuario en la sesión activa. Disponible en /api/auth/session y /api/auth/me.", security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", description = "Sesión activa válida", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin sesión activa", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping({"/session", "/me"})
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Sin sesión activa"));
        }

        Usuario usuario = usuarioRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        return ResponseEntity.ok(new UserResponse(usuario.getId(), usuario.getUsername()));
    }

    @Operation(summary = "Obtener token CSRF", description = "Endpoint público para obtener el token CSRF requerido en las peticiones que mutan estado (POST, PUT, DELETE).")
    @ApiResponse(responseCode = "200", description = "Token CSRF disponible")
    @GetMapping("/csrf")
    public CsrfToken getCsrfToken(CsrfToken token) {
        return token;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("El usuario y la contraseña son obligatorios"));
    }
}