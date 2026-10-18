package com.urleads.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.urleads.dto.AccessTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuanceServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-17T12:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private KeyPair keyPair;
    private JwtIssuanceService issuanceService;

    @BeforeEach
    void setUp() {
        keyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-issuance-key")
                .build();
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        issuanceService = new JwtIssuanceService(jwtEncoder, clock);
    }

    @Test
    @DisplayName("Emisión de token genera claims y vigencia exactos según R1 y R2")
    void issueAccessToken_ValidUserId_GeneratesExpectedClaims() throws Exception {
        AccessTokenResponse response = issuanceService.issueAccessToken(42L);

        assertThat(response).isNotNull();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(600L);
        assertThat(response.accessToken()).isNotBlank();

        SignedJWT signedJWT = SignedJWT.parse(response.accessToken());
        assertThat(signedJWT.getHeader().getAlgorithm().getName()).isEqualTo("RS256");

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("urleads-crm");
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getAudience()).containsExactlyInAnyOrder("crm-api", "comercial-api", "agenda-api");
        assertThat(claims.getStringClaim("scope")).isEqualTo("crm.leads.read comercial.access agenda.access");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(FIXED_NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(FIXED_NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("Rechazo de identificadores de usuario nulos, cero o negativos")
    void issueAccessToken_InvalidUserId_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> issuanceService.issueAccessToken(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> issuanceService.issueAccessToken(0L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> issuanceService.issueAccessToken(-5L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
