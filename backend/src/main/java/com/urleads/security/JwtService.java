package com.urleads.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final String expectedIssuer;
    private final String expectedAudience;

    public JwtService(
            @Value("${jwt.secret:urleads-backend-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256}") String secret,
            @Value("${jwt.issuer:urleads}") String expectedIssuer,
            @Value("${jwt.audience:urleads}") String expectedAudience
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
    }

    /**
     * Valida la firma criptográfica, emisor (iss), audiencia (aud) y expiración (exp).
     */
    public Claims parseAndValidateClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extrae el subject (sub) del token JWT tras validarlo.
     */
    public String extractSubject(String token) {
        Claims claims = parseAndValidateClaims(token);
        String sub = claims.getSubject();
        if (sub == null || sub.trim().isEmpty()) {
            throw new JwtException("El claim 'sub' no puede estar vacío");
        }
        return sub;
    }

    /**
     * Genera un token JWT para pruebas y autenticación con duración predeterminada de 60 minutos.
     */
    public String generateToken(String username) {
        return generateToken(username, 60);
    }

    /**
     * Genera un token JWT para pruebas y autenticación.
     */
    public String generateToken(String username, long durationMinutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuer(expectedIssuer)
                .audience().add(expectedAudience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(durationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generador flexible para pruebas de rechazo (tokens vencidos, emisor o audiencia incorrecta).
     */
    public String generateCustomToken(String username, String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }
}
