package com.urleads.security;

import com.urleads.dto.AccessTokenResponse;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Servicio de emisión de tokens de acceso JWT (Backend V2 C02).
 * Custodia la lógica de generación y firma con RS256 garantizando las audiencias,
 * alcances y vigencia canónicos de los contratos comunes Backend V2.
 */
public class JwtIssuanceService {

    public static final String ISSUER = "urleads-crm";
    public static final List<String> AUDIENCES = List.of("crm-api", "comercial-api", "agenda-api");
    public static final String SCOPES = "crm.leads.read comercial.access agenda.access";
    public static final long TTL_SECONDS = 600L;

    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    public JwtIssuanceService(JwtEncoder jwtEncoder, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    public AccessTokenResponse issueAccessToken(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Identificador de usuario inválido para emisión de token");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(TTL_SECONDS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .audience(AUDIENCES)
                .claim("scope", SCOPES)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new AccessTokenResponse(tokenValue, "Bearer", TTL_SECONDS);
    }

    public boolean isConfigured() {
        return jwtEncoder != null;
    }
}
