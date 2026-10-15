package com.urleads.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;

/**
 * Proveedor del usuario autenticado actual utilizando el contexto de Spring Security.
 * Resuelve de forma segura el usuario en base a la sesión real sin aceptar identificadores
 * desde el cliente.
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

        String username = auth.getName();
        return usuarioRepository.findByUsername(username)
                .map(Usuario::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
