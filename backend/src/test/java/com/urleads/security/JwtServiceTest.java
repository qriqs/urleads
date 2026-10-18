package com.urleads.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "urleads-backend-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256",
                "urleads",
                "urleads"
        );
    }

    @Test
    @DisplayName("Token válido debe extraer correctamente el username / subject")
    void validToken_ExtractsSubjectCorrectly() {
        String token = jwtService.generateToken("testuser", 60);
        String subject = jwtService.extractSubject(token);
        assertThat(subject).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Token expirado debe ser rechazado")
    void expiredToken_ThrowsJwtException() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String expiredToken = jwtService.generateCustomToken("testuser", "urleads", "urleads", past, past.plusSeconds(60));

        assertThatThrownBy(() -> jwtService.extractSubject(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Token con emisor incorrecto debe ser rechazado")
    void wrongIssuer_ThrowsJwtException() {
        Instant now = Instant.now();
        String wrongIssuerToken = jwtService.generateCustomToken("testuser", "otro-emisor", "urleads", now, now.plusSeconds(3600));

        assertThatThrownBy(() -> jwtService.extractSubject(wrongIssuerToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Token con audiencia incorrecta debe ser rechazado")
    void wrongAudience_ThrowsJwtException() {
        Instant now = Instant.now();
        String wrongAudienceToken = jwtService.generateCustomToken("testuser", "urleads", "otra-audiencia", now, now.plusSeconds(3600));

        assertThatThrownBy(() -> jwtService.extractSubject(wrongAudienceToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Token corrupto debe lanzar JwtException")
    void corruptToken_ThrowsJwtException() {
        assertThatThrownBy(() -> jwtService.extractSubject("token-completamente-invalido"))
                .isInstanceOf(JwtException.class);
    }
}
