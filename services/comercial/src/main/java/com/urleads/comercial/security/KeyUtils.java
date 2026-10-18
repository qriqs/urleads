package com.urleads.comercial.security;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class KeyUtils {

    private KeyUtils() {}

    public static RSAPublicKey loadPublicKey(String location, ResourceLoader resourceLoader) {
        try {
            Resource resource = resourceLoader.getResource(location);
            try (InputStream is = resource.getInputStream()) {
                String keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return parsePublicKey(keyContent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key from " + location, e);
        }
    }

    public static RSAPublicKey parsePublicKey(String pem) {
        try {
            String sanitized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(sanitized);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RSA public key PEM format", e);
        }
    }
}
