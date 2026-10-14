package com.urleads.security;

import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UsuarioRepository usuarioRepository;

    public UserDetailsServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.trim().isEmpty()) {
            throw new UsernameNotFoundException("Usuario no proporcionado");
        }

        String sanitizedUsername = username.trim();
        log.debug("Buscando credenciales para usuario: {}", sanitizedUsername);

        Usuario usuario = usuarioRepository.findByUsername(sanitizedUsername)
            .orElseThrow(() -> {
                log.warn("Intento de autenticacion fallido: usuario no encontrado");
                return new UsernameNotFoundException("Credenciales invalidas");
            });

        return new User(
            usuario.getUsername(),
            usuario.getPasswordHash(),
            Collections.emptyList()
        );
    }
}