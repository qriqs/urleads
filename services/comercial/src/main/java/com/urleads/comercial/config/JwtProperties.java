package com.urleads.comercial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "comercial.security.jwt")
public class JwtProperties {

    private String publicKeyLocation = "classpath:keys/crm-public.pem";
    private String issuer = "urleads-crm";
    private String audience = "comercial-api";
    private String requiredScope = "comercial.access";

    public String getPublicKeyLocation() {
        return publicKeyLocation;
    }

    public void setPublicKeyLocation(String publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getRequiredScope() {
        return requiredScope;
    }

    public void setRequiredScope(String requiredScope) {
        this.requiredScope = requiredScope;
    }
}
