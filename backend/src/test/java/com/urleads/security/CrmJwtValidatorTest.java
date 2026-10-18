package com.urleads.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrmJwtValidatorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-17T12:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private CrmJwtValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CrmJwtValidator("urleads-crm", "crm-api", Duration.ofSeconds(30), clock);
    }

    private Jwt buildJwt(String issuer, String subject, List<String> audiences, Instant exp, Instant nbf) {
        Map<String, Object> claims = new HashMap<>();
        if (issuer != null) claims.put("iss", issuer);
        if (subject != null) claims.put("sub", subject);
        if (audiences != null) claims.put("aud", audiences);
        if (exp != null) claims.put("exp", exp);
        if (nbf != null) claims.put("nbf", nbf);

        Instant issuedAt = exp != null ? exp.minusSeconds(600) : FIXED_NOW;

        return new Jwt(
                "test-token",
                issuedAt,
                exp,
                Map.of("alg", "RS256"),
                claims
        );
    }

    @Test
    @DisplayName("Token con todos los claims correctos es validado exitosamente")
    void validate_ValidToken_ReturnsSuccess() {
        Jwt jwt = buildJwt("urleads-crm", "42", List.of("crm-api", "comercial-api"), FIXED_NOW.plusSeconds(600), FIXED_NOW);
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Constructor rechaza emisor no canónico o margen de reloj fuera de rango (F1)")
    void constructor_InvalidParameters_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new CrmJwtValidator("otro-emisor", "crm-api", Duration.ofSeconds(30), clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("urleads-crm");

        assertThatThrownBy(() -> new CrmJwtValidator("urleads-crm", "crm-api", Duration.ofSeconds(3600), clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 segundos");

        assertThatThrownBy(() -> new CrmJwtValidator("urleads-crm", "crm-api", Duration.ofSeconds(-5), clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 segundos");
    }

    @Test
    @DisplayName("Emisor incorrecto o nulo genera error de validación")
    void validate_InvalidIssuer_ReturnsError() {
        Jwt wrongIssuer = buildJwt("otro-emisor", "42", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null);
        assertThat(validator.validate(wrongIssuer).hasErrors()).isTrue();

        Jwt nullIssuer = buildJwt(null, "42", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null);
        assertThat(validator.validate(nullIssuer).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Audiencia que no incluye crm-api genera error de validación")
    void validate_InvalidAudience_ReturnsError() {
        Jwt wrongAudience = buildJwt("urleads-crm", "42", List.of("comercial-api", "agenda-api"), FIXED_NOW.plusSeconds(600), null);
        assertThat(validator.validate(wrongAudience).hasErrors()).isTrue();

        Jwt nullAudience = buildJwt("urleads-crm", "42", null, FIXED_NOW.plusSeconds(600), null);
        assertThat(validator.validate(nullAudience).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Expiración obligatoria: token sin claim exp es rechazado")
    void validate_MissingExp_ReturnsError() {
        Jwt noExp = buildJwt("urleads-crm", "42", List.of("crm-api"), null, null);
        assertThat(validator.validate(noExp).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Expiración con margen de reloj de 30 segundos: tolerado dentro de 30s, rechazado pasados 30s")
    void validate_ExpirationWithSkew_Behavior() {
        // Expiró hace 25 segundos (dentro de tolerancia de 30 segundos)
        Jwt withinSkew = buildJwt("urleads-crm", "42", List.of("crm-api"), FIXED_NOW.minusSeconds(25), null);
        assertThat(validator.validate(withinSkew).hasErrors()).isFalse();

        // Expiró hace 35 segundos (fuera de tolerancia de 30 segundos)
        Jwt outsideSkew = buildJwt("urleads-crm", "42", List.of("crm-api"), FIXED_NOW.minusSeconds(35), null);
        assertThat(validator.validate(outsideSkew).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Claim nbf en el futuro con margen de reloj de 30 segundos")
    void validate_NotBeforeWithSkew_Behavior() {
        // Válido a partir de 15 segundos en el futuro (dentro de tolerancia)
        Jwt withinSkew = buildJwt("urleads-crm", "42", List.of("crm-api"), FIXED_NOW.plusSeconds(600), FIXED_NOW.plusSeconds(15));
        assertThat(validator.validate(withinSkew).hasErrors()).isFalse();

        // Válido a partir de 45 segundos en el futuro (fuera de tolerancia)
        Jwt outsideSkew = buildJwt("urleads-crm", "42", List.of("crm-api"), FIXED_NOW.plusSeconds(600), FIXED_NOW.plusSeconds(45));
        assertThat(validator.validate(outsideSkew).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Validación del sujeto (sub): rechaza texto, UUID, cero, negativos y overflow")
    void validate_InvalidSubjects_ReturnErrors() {
        assertThat(validator.validate(buildJwt("urleads-crm", "admin", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null)).hasErrors()).isTrue();
        assertThat(validator.validate(buildJwt("urleads-crm", "550e8400-e29b-41d4-a716-446655440000", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null)).hasErrors()).isTrue();
        assertThat(validator.validate(buildJwt("urleads-crm", "0", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null)).hasErrors()).isTrue();
        assertThat(validator.validate(buildJwt("urleads-crm", "-42", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null)).hasErrors()).isTrue();
        assertThat(validator.validate(buildJwt("urleads-crm", null, List.of("crm-api"), FIXED_NOW.plusSeconds(600), null)).hasErrors()).isTrue();
        assertThat(validator.validate(buildJwt("urleads-crm", "  ", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null)).hasErrors()).isTrue();
        assertThat(validator.validate(buildJwt("urleads-crm", "99999999999999999999999999999", List.of("crm-api"), FIXED_NOW.plusSeconds(600), null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Claim scope no de tipo String es rechazado como error de validación")
    void validate_NonStringScope_ReturnsError() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "urleads-crm");
        claims.put("sub", "42");
        claims.put("aud", List.of("crm-api"));
        claims.put("exp", FIXED_NOW.plusSeconds(600));
        claims.put("scope", 42); // Tipo numérico inválido

        Jwt jwt = new Jwt("test-token", FIXED_NOW, FIXED_NOW.plusSeconds(600), Map.of("alg", "RS256"), claims);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
}
