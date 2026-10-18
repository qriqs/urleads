package com.urleads.comercial.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public final class JwtTestUtils {

    private static final RSAPrivateKey CRM_PRIVATE_KEY;

    static {
        try {
            Resource resource = new DefaultResourceLoader().getResource("classpath:keys/crm-private.pem");
            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String sanitized = content
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
                byte[] decoded = Base64.getDecoder().decode(sanitized);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
                CRM_PRIVATE_KEY = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JwtTestUtils() {}

    public static String generateValidToken() {
        return generateToken("42", "urleads-crm", List.of("comercial-api"), "comercial.access", Instant.now().plusSeconds(600));
    }

    public static String generateValidToken(String sub, String scope) {
        return generateToken(sub, "urleads-crm", List.of("comercial-api"), scope, Instant.now().plusSeconds(600));
    }

    public static String generateTokenWithAudiences(List<String> audiences) {
        return generateToken("42", "urleads-crm", audiences, "comercial.access", Instant.now().plusSeconds(600));
    }

    public static String generateExpiredToken() {
        return generateToken("42", "urleads-crm", List.of("comercial-api"), "comercial.access", Instant.now().minusSeconds(120));
    }

    public static String generateTokenWithInvalidIssuer(String invalidIssuer) {
        return generateToken("42", invalidIssuer, List.of("comercial-api"), "comercial.access", Instant.now().plusSeconds(600));
    }

    public static String generateTokenWithInvalidSubject(String invalidSub) {
        return generateToken(invalidSub, "urleads-crm", List.of("comercial-api"), "comercial.access", Instant.now().plusSeconds(600));
    }

    public static String generateToken(
        String sub,
        String issuer,
        List<String> audiences,
        String scope,
        Instant expiresAt
    ) {
        return buildSignedToken(CRM_PRIVATE_KEY, sub, issuer, audiences, scope, expiresAt);
    }

    public static String generateTokenWithUnknownKey() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            return buildSignedToken(
                (RSAPrivateKey) kp.getPrivate(),
                "42",
                "urleads-crm",
                List.of("comercial-api"),
                "comercial.access",
                Instant.now().plusSeconds(600)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate foreign key token", e);
        }
    }

    private static String buildSignedToken(
        RSAPrivateKey privateKey,
        String sub,
        String issuer,
        List<String> audiences,
        String scope,
        Instant expiresAt
    ) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audiences)
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt));

            if (sub != null) {
                builder.subject(sub);
            }
            if (scope != null) {
                builder.claim("scope", scope);
            }

            SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                builder.build()
            );
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build signed JWT", e);
        }
    }
}
