package com.urleads.security;

import com.urleads.config.JwtProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validador estricto de tokens JWT para el servicio CRM (Backend V2 C02).
 * Verifica emisor exacto, audiencia requerida, vigencia temporal con margen de reloj
 * acotado y formato decimal numérico positivo del claim sujeto (sub).
 */
public class CrmJwtValidator implements OAuth2TokenValidator<Jwt> {

    public static final Duration MAX_ALLOWED_CLOCK_SKEW = Duration.ofSeconds(30);

    private final String expectedIssuer;
    private final String requiredAudience;
    private final Duration clockSkew;
    private final Clock clock;

    public CrmJwtValidator(String expectedIssuer, String requiredAudience, Duration clockSkew, Clock clock) {
        if (expectedIssuer == null || !JwtProperties.CANONICAL_ISSUER.equals(expectedIssuer.trim())) {
            throw new IllegalArgumentException("El emisor esperado debe ser exactamente '" + JwtProperties.CANONICAL_ISSUER + "'");
        }
        if (clockSkew == null || clockSkew.isNegative() || clockSkew.compareTo(MAX_ALLOWED_CLOCK_SKEW) > 0) {
            throw new IllegalArgumentException("La tolerancia de reloj debe estar entre 0 y 30 segundos");
        }

        this.expectedIssuer = expectedIssuer;
        this.requiredAudience = requiredAudience;
        this.clockSkew = clockSkew;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<OAuth2Error> errors = new ArrayList<>();

        // 1. Validar emisor (iss) exacto
        String issuer = jwt.getClaimAsString("iss");
        if (issuer == null || !expectedIssuer.equals(issuer)) {
            errors.add(new OAuth2Error("invalid_token", "El claim 'iss' es inválido o no coincide con el emisor esperado", null));
        }

        // 2. Validar que la audiencia (aud) contenga el identificador del servicio
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(requiredAudience)) {
            errors.add(new OAuth2Error("invalid_token", "El claim 'aud' no incluye la audiencia requerida para este servicio", null));
        }

        // 3. Validar expiración (exp) obligatoria con tolerancia de reloj
        Instant now = clock.instant();
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            errors.add(new OAuth2Error("invalid_token", "El claim 'exp' es obligatorio y está ausente", null));
        } else if (expiresAt.isBefore(now.minus(clockSkew))) {
            errors.add(new OAuth2Error("invalid_token", "El token ha expirado", null));
        }

        // 4. Validar fecha de inicio de vigencia (nbf) si está presente
        Instant notBefore = jwt.getNotBefore();
        if (notBefore != null && notBefore.isAfter(now.plus(clockSkew))) {
            errors.add(new OAuth2Error("invalid_token", "El token aún no es válido según su claim 'nbf'", null));
        }

        // 5. Validar sujeto (sub): cadena decimal estrictamente numérica que representa un Long positivo
        String sub = jwt.getSubject();
        if (sub == null || !sub.matches("^[0-9]+$")) {
            errors.add(new OAuth2Error("invalid_token", "El claim 'sub' debe ser una cadena decimal estrictamente numérica", null));
        } else {
            try {
                long userId = Long.parseLong(sub);
                if (userId <= 0) {
                    errors.add(new OAuth2Error("invalid_token", "El claim 'sub' debe representar un identificador de usuario positivo", null));
                }
            } catch (NumberFormatException e) {
                errors.add(new OAuth2Error("invalid_token", "El claim 'sub' excede el rango numérico permitido para un identificador", null));
            }
        }

        // 6. Validar que si el claim 'scope' está presente sea una cadena de texto
        Object scopeClaim = jwt.getClaims().get("scope");
        if (scopeClaim != null && !(scopeClaim instanceof String)) {
            errors.add(new OAuth2Error("invalid_token", "El claim 'scope' debe ser una cadena de texto separada por espacios", null));
        }

        if (errors.isEmpty()) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(errors);
    }
}
