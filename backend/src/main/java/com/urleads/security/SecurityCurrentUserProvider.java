package com.urleads.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;

/**
 * Proveedor del usuario autenticado actual utilizando el contexto de Spring Security.
 * Resuelve de forma segura el usuario en base a la sesión real o token JWT verificado
 * sin aceptar identificadores desde el cliente.
 */
@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    private final UsuarioRepository usuarioRepository;

    public SecurityCurrentUserProvider(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        // Manejo de identidad autenticada mediante JWT verificado (rutas internas Backend V2)
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getToken().getSubject();
            if (sub == null || !sub.matches("^[0-9]+$")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sujeto de token JWT inválido");
            }
            try {
                long userId = Long.parseLong(sub);
                if (userId <= 0) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sujeto de token JWT debe ser un ID positivo");
                }
                return userId;
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sujeto de token JWT excede rango numérico");
            }
        }

        // Manejo de identidad autenticada mediante sesión web (rutas legadas /api/**)
        String username = auth.getName();
        return usuarioRepository.findByUsername(username)
                .map(Usuario::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
