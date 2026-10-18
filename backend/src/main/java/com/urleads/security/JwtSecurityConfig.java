package com.urleads.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.urleads.config.JwtProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

/**
 * Configuración criptográfica de componentes JWT activada condicionalmente cuando
 * {@code urleads.jwt.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(prefix = "urleads.jwt", name = "enabled", havingValue = "true")
public class JwtSecurityConfig {

    @Bean
    public RSAPrivateKey jwtPrivateKey(JwtProperties properties, JwtKeyLoader keyLoader) {
        return keyLoader.loadPrivateKey(properties);
    }

    @Bean
    public RSAPublicKey jwtPublicKey(JwtProperties properties, JwtKeyLoader keyLoader) {
        return keyLoader.loadPublicKey(properties);
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("urleads-crm-key")
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey, JwtProperties properties, Clock clock) {
        long skewSeconds = properties.getClockSkewSeconds();
        if (skewSeconds < 0 || skewSeconds > JwtProperties.MAX_CLOCK_SKEW_SECONDS) {
            throw new IllegalStateException("La tolerancia de reloj (clock skew) debe ser entre 0 y " + JwtProperties.MAX_CLOCK_SKEW_SECONDS + " segundos");
        }
        if (!JwtProperties.CANONICAL_ISSUER.equals(properties.getIssuer())) {
            throw new IllegalStateException("El emisor JWT debe ser exactamente '" + JwtProperties.CANONICAL_ISSUER + "'");
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        CrmJwtValidator validator = new CrmJwtValidator(
                JwtProperties.CANONICAL_ISSUER,
                "crm-api",
                Duration.ofSeconds(skewSeconds),
                clock
        );

        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtIssuanceService jwtIssuanceService(JwtEncoder jwtEncoder, Clock clock) {
        return new JwtIssuanceService(jwtEncoder, clock);
    }
}
