package com.urleads.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades de configuración para identidad y validación JWT (Backend V2 C02).
 * Protege los valores canónicos inmutables (emisor exacto 'urleads-crm', margen de reloj
 * máximo de 30 segundos y TTL fijo de 600 segundos) evitando debilitar la seguridad.
 */
@Configuration
@ConfigurationProperties(prefix = "urleads.jwt")
public class JwtProperties {

    public static final String CANONICAL_ISSUER = "urleads-crm";
    public static final long MAX_CLOCK_SKEW_SECONDS = 30L;
    public static final long CANONICAL_TTL_SECONDS = 600L;

    private boolean enabled = false;
    private String issuer = CANONICAL_ISSUER;
    private String privateKeyLocation;
    private String publicKeyLocation;
    private String privateKeyValue;
    private String publicKeyValue;
    private long ttlSeconds = CANONICAL_TTL_SECONDS;
    private long clockSkewSeconds = MAX_CLOCK_SKEW_SECONDS;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return CANONICAL_ISSUER;
    }

    public void setIssuer(String issuer) {
        if (issuer != null && !issuer.isBlank() && !CANONICAL_ISSUER.equals(issuer.trim())) {
            throw new IllegalArgumentException("El emisor JWT no puede modificarse; debe ser '" + CANONICAL_ISSUER + "'");
        }
        this.issuer = CANONICAL_ISSUER;
    }

    public String getPrivateKeyLocation() {
        return privateKeyLocation;
    }

    public void setPrivateKeyLocation(String privateKeyLocation) {
        this.privateKeyLocation = privateKeyLocation;
    }

    public String getPublicKeyLocation() {
        return publicKeyLocation;
    }

    public void setPublicKeyLocation(String publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }

    public String getPrivateKeyValue() {
        return privateKeyValue;
    }

    public void setPrivateKeyValue(String privateKeyValue) {
        this.privateKeyValue = privateKeyValue;
    }

    public String getPublicKeyValue() {
        return publicKeyValue;
    }

    public void setPublicKeyValue(String publicKeyValue) {
        this.publicKeyValue = publicKeyValue;
    }

    public long getTtlSeconds() {
        return CANONICAL_TTL_SECONDS;
    }

    public void setTtlSeconds(long ttlSeconds) {
        if (ttlSeconds != CANONICAL_TTL_SECONDS) {
            throw new IllegalArgumentException(
                    "El tiempo de vida (TTL) del token JWT es fijo en " + CANONICAL_TTL_SECONDS + " segundos"
            );
        }
        this.ttlSeconds = CANONICAL_TTL_SECONDS;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        if (clockSkewSeconds < 0 || clockSkewSeconds > MAX_CLOCK_SKEW_SECONDS) {
            throw new IllegalArgumentException(
                    "La tolerancia de reloj (clock skew) debe ser entre 0 y " + MAX_CLOCK_SKEW_SECONDS + " segundos"
            );
        }
        this.clockSkewSeconds = clockSkewSeconds;
    }
}
