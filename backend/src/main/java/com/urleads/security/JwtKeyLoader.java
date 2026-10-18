package com.urleads.security;

import com.urleads.config.JwtProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Cargador de claves criptográficas asimétricas RSA para firmado y verificación de tokens JWT.
 * Soporta claves privadas en formato PKCS#8 y claves públicas en formato X.509 (>= 2048 bits).
 */
@Component
public class JwtKeyLoader {

    public static final int MINIMUM_RSA_KEY_SIZE_BITS = 2048;

    private final ResourceLoader resourceLoader;

    public JwtKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public RSAPrivateKey loadPrivateKey(JwtProperties properties) {
        String pem = properties.getPrivateKeyValue();
        if (pem == null || pem.isBlank()) {
            String location = properties.getPrivateKeyLocation();
            if (location != null && !location.isBlank()) {
                pem = readResourceContent(location);
            }
        }

        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Configuración JWT inválida: falta la clave privada RSA (ubicación o valor no especificado)");
        }

        return parsePrivateKey(pem);
    }

    public RSAPublicKey loadPublicKey(JwtProperties properties) {
        String pem = properties.getPublicKeyValue();
        if (pem == null || pem.isBlank()) {
            String location = properties.getPublicKeyLocation();
            if (location != null && !location.isBlank()) {
                pem = readResourceContent(location);
            }
        }

        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Configuración JWT inválida: falta la clave pública RSA (ubicación o valor no especificado)");
        }

        return parsePublicKey(pem);
    }

    public static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            String clean = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(clean);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)) {
                throw new IllegalStateException("Configuración JWT inválida: la clave privada no es de tipo RSA");
            }

            if (rsaPrivateKey.getModulus().bitLength() < MINIMUM_RSA_KEY_SIZE_BITS) {
                throw new IllegalStateException("Configuración JWT inválida: la clave privada RSA debe tener al menos " + MINIMUM_RSA_KEY_SIZE_BITS + " bits");
            }

            return rsaPrivateKey;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Configuración JWT inválida: formato de clave privada no válido (se requiere PKCS#8 en formato PEM)");
        }
    }

    public static RSAPublicKey parsePublicKey(String pem) {
        try {
            String clean = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(clean);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalStateException("Configuración JWT inválida: la clave pública no es de tipo RSA");
            }

            if (rsaPublicKey.getModulus().bitLength() < MINIMUM_RSA_KEY_SIZE_BITS) {
                throw new IllegalStateException("Configuración JWT inválida: la clave pública RSA debe tener al menos " + MINIMUM_RSA_KEY_SIZE_BITS + " bits");
            }

            return rsaPublicKey;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Configuración JWT inválida: formato de clave pública no válido (se requiere X.509 en formato PEM)");
        }
    }

    private String readResourceContent(String location) {
        try {
            Resource resource;
            if (location.startsWith("classpath:") || location.startsWith("file:")) {
                resource = resourceLoader.getResource(location);
            } else {
                File file = new File(location);
                if (file.exists() && file.isFile()) {
                    resource = new FileSystemResource(file);
                } else {
                    resource = resourceLoader.getResource(location);
                }
            }

            if (!resource.exists()) {
                throw new IllegalStateException("Configuración JWT inválida: el archivo de clave especificado no existe");
            }

            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalStateException("Configuración JWT inválida: no se pudo leer el archivo de clave");
        }
    }
}
