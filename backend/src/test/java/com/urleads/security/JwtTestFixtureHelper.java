package com.urleads.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Utilidades para generación dinámica de pares de claves RSA y tokens JWT sintéticos en pruebas.
 * Ningún secreto ni clave se persiste en disco ni se incluye en el control de versiones.
 */
public final class JwtTestFixtureHelper {

    private JwtTestFixtureHelper() {
    }

    public static KeyPair generateRsaKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo RSA no disponible en entorno JVM", e);
        }
    }

    public static String toPkcs8Pem(PrivateKey key) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    public static String toX509Pem(PublicKey key) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static String createSignedJwt(
            PrivateKey privateKey,
            String issuer,
            String subject,
            List<String> audiences,
            String scope,
            Instant issuedAt,
            Instant expiresAt,
            Instant notBefore) throws Exception {

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (subject != null) {
            builder.subject(subject);
        }
        if (audiences != null) {
            builder.audience(audiences);
        }
        if (scope != null) {
            builder.claim("scope", scope);
        }
        if (issuedAt != null) {
            builder.issueTime(Date.from(issuedAt));
        }
        if (expiresAt != null) {
            builder.expirationTime(Date.from(expiresAt));
        }
        if (notBefore != null) {
            builder.notBeforeTime(Date.from(notBefore));
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                builder.build()
        );
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    public static String createTamperedJwt(String validJwt) {
        String[] parts = validJwt.split("\\.");
        if (parts.length != 3) {
            return validJwt + "tampered";
        }
        // Modificar un carácter del payload (parte central)
        char[] payloadChars = parts[1].toCharArray();
        payloadChars[payloadChars.length / 2] = (payloadChars[payloadChars.length / 2] == 'A') ? 'B' : 'A';
        return parts[0] + "." + new String(payloadChars) + "." + parts[2];
    }

    public static String createNoneAlgJwt(
            String issuer,
            String subject,
            List<String> audiences,
            String scope,
            Instant issuedAt,
            Instant expiresAt) {

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        if (issuer != null) builder.issuer(issuer);
        if (subject != null) builder.subject(subject);
        if (audiences != null) builder.audience(audiences);
        if (scope != null) builder.claim("scope", scope);
        if (issuedAt != null) builder.issueTime(Date.from(issuedAt));
        if (expiresAt != null) builder.expirationTime(Date.from(expiresAt));

        PlainJWT plainJWT = new PlainJWT(builder.build());
        return plainJWT.serialize();
    }

    public static String createHmacJwt(
            byte[] hmacKeyBytes,
            String issuer,
            String subject,
            List<String> audiences,
            String scope,
            Instant issuedAt,
            Instant expiresAt) throws Exception {

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        if (issuer != null) builder.issuer(issuer);
        if (subject != null) builder.subject(subject);
        if (audiences != null) builder.audience(audiences);
        if (scope != null) builder.claim("scope", scope);
        if (issuedAt != null) builder.issueTime(Date.from(issuedAt));
        if (expiresAt != null) builder.expirationTime(Date.from(expiresAt));

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).build(),
                builder.build()
        );
        signedJWT.sign(new MACSigner(hmacKeyBytes));
        return signedJWT.serialize();
    }
}
