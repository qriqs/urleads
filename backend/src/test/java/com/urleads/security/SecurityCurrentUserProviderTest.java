package com.urleads.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;

class SecurityCurrentUserProviderTest {

    private UsuarioRepository usuarioRepository;
    private SecurityCurrentUserProvider provider;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        provider = new SecurityCurrentUserProvider(usuarioRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_sinAutenticacion_lanza401() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> provider.getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getCurrentUserId_usuarioAnonimo_lanza401() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null));

        assertThatThrownBy(() -> provider.getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getCurrentUserId_usuarioNoExisteEnBD_lanza401() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pwd", Collections.emptyList()));

        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getCurrentUserId_usuarioExiste_retornaId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pwd", Collections.emptyList()));

        Usuario u = new Usuario();
        u.setId(42L);
        u.setUsername("admin");
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(u));

        Long id = provider.getCurrentUserId();
        assertThat(id).isEqualTo(42L);
    }
}
