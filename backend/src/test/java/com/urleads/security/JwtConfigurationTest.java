package com.urleads.security;

import com.urleads.config.ClockConfig;
import com.urleads.config.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JwtSecurityConfig.class,
                    JwtKeyLoader.class,
                    ClockConfig.class
            ));

    // =========================================================================
    // F1: Protección contra debilitamiento de emisor, tolerancia y TTL
    // =========================================================================

    @Test
    @DisplayName("F1: JwtProperties rechaza modificación de emisor canónico hacia valor no canónico")
    void jwtProperties_RejectsNonCanonicalIssuer() {
        JwtProperties properties = new JwtProperties();
        assertThat(properties.getIssuer()).isEqualTo("urleads-crm");

        assertThatThrownBy(() -> properties.setIssuer("different-issuer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("urleads-crm");

        // Permite reasignar el canónico o nulo/vacío conservando el canónico
        properties.setIssuer("urleads-crm");
        assertThat(properties.getIssuer()).isEqualTo("urleads-crm");
    }

    @Test
    @DisplayName("F1: JwtProperties rechaza margen de reloj fuera del rango 0 a 30 segundos")
    void jwtProperties_RejectsClockSkewOutOfRange() {
        JwtProperties properties = new JwtProperties();
        assertThat(properties.getClockSkewSeconds()).isEqualTo(30L);

        assertThatThrownBy(() -> properties.setClockSkewSeconds(3600L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 segundos");

        assertThatThrownBy(() -> properties.setClockSkewSeconds(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 segundos");

        properties.setClockSkewSeconds(15L);
        assertThat(properties.getClockSkewSeconds()).isEqualTo(15L);
    }

    @Test
    @DisplayName("F1: JwtProperties rechaza modificación de TTL diferente a 600 segundos")
    void jwtProperties_RejectsTtlDifferentFrom600() {
        JwtProperties properties = new JwtProperties();
        assertThat(properties.getTtlSeconds()).isEqualTo(600L);

        assertThatThrownBy(() -> properties.setTtlSeconds(3600L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("600 segundos");
    }

    @Test
    @DisplayName("F1: JwtSecurityConfig rechaza emisor o margen de reloj no canónico al construir el decoder")
    void jwtSecurityConfig_RejectsInvalidPropertiesAtDecoderCreation() {
        KeyPair keyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        JwtSecurityConfig config = new JwtSecurityConfig();

        JwtProperties invalidSkewProps = new JwtProperties() {
            @Override
            public long getClockSkewSeconds() {
                return 3600L;
            }
        };
        assertThatThrownBy(() -> config.jwtDecoder((RSAPublicKey) keyPair.getPublic(), invalidSkewProps, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("30 segundos");

        JwtProperties invalidIssuerProps = new JwtProperties() {
            @Override
            public String getIssuer() {
                return "different-issuer";
            }
        };
        assertThatThrownBy(() -> config.jwtDecoder((RSAPublicKey) keyPair.getPublic(), invalidIssuerProps, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("urleads-crm");
    }

    @Test
    @DisplayName("F1: Decodificador no valida tokens con emisor ajeno o expiración mayor a 30 segundos")
    void decoder_RejectsTokensWithNonCanonicalIssuerOrExpiredBeyond30s() throws Exception {
        KeyPair keyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        JwtSecurityConfig config = new JwtSecurityConfig();
        JwtProperties properties = new JwtProperties();

        JwtDecoder decoder = config.jwtDecoder((RSAPublicKey) keyPair.getPublic(), properties, Clock.systemUTC());
        Instant now = Instant.now();

        // 1. Token con emisor ajeno
        String wrongIssuerToken = JwtTestFixtureHelper.createSignedJwt(
                keyPair.getPrivate(), "different-issuer", "42", List.of("crm-api"), "crm.leads.read",
                now, now.plusSeconds(600), null
        );
        assertThatThrownBy(() -> decoder.decode(wrongIssuerToken))
                .isInstanceOf(JwtValidationException.class);

        // 2. Token expirado hace 900 segundos
        String expired900sToken = JwtTestFixtureHelper.createSignedJwt(
                keyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                now.minusSeconds(1500), now.minusSeconds(900), null
        );
        assertThatThrownBy(() -> decoder.decode(expired900sToken))
                .isInstanceOf(JwtValidationException.class);
    }

    // =========================================================================
    // A17 / F1: Evidencia de arranque y fallo rápido en ApplicationContext
    // =========================================================================

    @Test
    @DisplayName("A17: Con JWT habilitado y sin claves configuradas, el arranque del contexto falla de forma rápida y clara")
    void context_WithJwtEnabledAndMissingKeys_FailsFast() {
        contextRunner
                .withUserConfiguration(JwtProperties.class)
                .withPropertyValues("urleads.jwt.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("falta la clave privada");
                });
    }

    @Test
    @DisplayName("A17: Con JWT habilitado y clave menor a 2048 bits, el arranque del contexto falla de forma rápida")
    void context_WithJwtEnabledAndWeakKey_FailsFast() {
        KeyPair weakPair = JwtTestFixtureHelper.generateRsaKeyPair(1024);
        String privPem = JwtTestFixtureHelper.toPkcs8Pem(weakPair.getPrivate());
        String pubPem = JwtTestFixtureHelper.toX509Pem(weakPair.getPublic());

        contextRunner
                .withPropertyValues("urleads.jwt.enabled=true")
                .withBean(JwtProperties.class, () -> {
                    JwtProperties p = new JwtProperties();
                    p.setEnabled(true);
                    p.setPrivateKeyValue(privPem);
                    p.setPublicKeyValue(pubPem);
                    return p;
                })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("2048 bits");
                });
    }

    @Test
    @DisplayName("A17: Con JWT habilitado y clave corrupta, el arranque del contexto falla de forma rápida")
    void context_WithJwtEnabledAndCorruptedKey_FailsFast() {
        contextRunner
                .withPropertyValues("urleads.jwt.enabled=true")
                .withBean(JwtProperties.class, () -> {
                    JwtProperties p = new JwtProperties();
                    p.setEnabled(true);
                    p.setPrivateKeyValue("-----BEGIN PRIVATE KEY-----\nCORRUPT\n-----END PRIVATE KEY-----");
                    p.setPublicKeyValue("-----BEGIN PUBLIC KEY-----\nCORRUPT\n-----END PUBLIC KEY-----");
                    return p;
                })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("PKCS#8");
                });
    }

    @Test
    @DisplayName("A17: Con JWT habilitado y claves válidas de 2048 bits, el contexto inicia con todos los beans")
    void context_WithJwtEnabledAndValidKeys_StartsSuccessfully() {
        KeyPair keyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        String privPem = JwtTestFixtureHelper.toPkcs8Pem(keyPair.getPrivate());
        String pubPem = JwtTestFixtureHelper.toX509Pem(keyPair.getPublic());

        contextRunner
                .withPropertyValues("urleads.jwt.enabled=true")
                .withBean(JwtProperties.class, () -> {
                    JwtProperties p = new JwtProperties();
                    p.setEnabled(true);
                    p.setPrivateKeyValue(privPem);
                    p.setPublicKeyValue(pubPem);
                    return p;
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtEncoder.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasSingleBean(JwtIssuanceService.class);
                });
    }

    @Test
    @DisplayName("A17: Con JWT deshabilitado (por defecto), el contexto arranca sin beans criptográficos")
    void context_WithJwtDisabled_StartsWithoutCryptoBeans() {
        contextRunner
                .withUserConfiguration(JwtProperties.class)
                .withPropertyValues("urleads.jwt.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JwtEncoder.class);
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                    assertThat(context).doesNotHaveBean(JwtIssuanceService.class);
                });
    }
}
