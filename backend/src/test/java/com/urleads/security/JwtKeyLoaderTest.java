package com.urleads.security;

import com.urleads.config.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyLoaderTest {

    private final JwtKeyLoader keyLoader = new JwtKeyLoader(new DefaultResourceLoader());

    @Test
    @DisplayName("Carga y parseo exitoso de clave privada PKCS#8 y clave pública X.509 de 2048 bits")
    void parseKeys_Valid2048BitKeys_Succeeds() {
        KeyPair keyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        String privPem = JwtTestFixtureHelper.toPkcs8Pem(keyPair.getPrivate());
        String pubPem = JwtTestFixtureHelper.toX509Pem(keyPair.getPublic());

        RSAPrivateKey privateKey = JwtKeyLoader.parsePrivateKey(privPem);
        RSAPublicKey publicKey = JwtKeyLoader.parsePublicKey(pubPem);

        assertThat(privateKey).isNotNull();
        assertThat(privateKey.getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
        assertThat(publicKey).isNotNull();
        assertThat(publicKey.getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
    }

    @Test
    @DisplayName("Rechazo de claves RSA con longitud menor a 2048 bits (ej. 1024 bits)")
    void parseKeys_LessThan2048Bits_ThrowsIllegalStateException() {
        KeyPair weakPair = JwtTestFixtureHelper.generateRsaKeyPair(1024);
        String privPem = JwtTestFixtureHelper.toPkcs8Pem(weakPair.getPrivate());
        String pubPem = JwtTestFixtureHelper.toX509Pem(weakPair.getPublic());

        assertThatThrownBy(() -> JwtKeyLoader.parsePrivateKey(privPem))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048 bits");

        assertThatThrownBy(() -> JwtKeyLoader.parsePublicKey(pubPem))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048 bits");
    }

    @Test
    @DisplayName("Rechazo de formato PEM corrupto o inválido")
    void parseKeys_CorruptedPem_ThrowsIllegalStateException() {
        String invalidPriv = "-----BEGIN PRIVATE KEY-----\nNOT_BASE64!\n-----END PRIVATE KEY-----";
        String invalidPub = "-----BEGIN PUBLIC KEY-----\nNOT_BASE64!\n-----END PUBLIC KEY-----";

        assertThatThrownBy(() -> JwtKeyLoader.parsePrivateKey(invalidPriv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8");

        assertThatThrownBy(() -> JwtKeyLoader.parsePublicKey(invalidPub))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X.509");
    }

    @Test
    @DisplayName("Carga de claves desde archivo con permisos restrictivos y ciclo de vida determinista (F2)")
    void loadKeys_FromFileSystem_Succeeds() throws Exception {
        KeyPair keyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        String privPem = JwtTestFixtureHelper.toPkcs8Pem(keyPair.getPrivate());
        String pubPem = JwtTestFixtureHelper.toX509Pem(keyPair.getPublic());

        String configuredBase = System.getProperty("urleads.test.temp-dir");
        Path baseDir = (configuredBase != null && !configuredBase.isBlank())
                ? Paths.get(configuredBase)
                : Paths.get(System.getProperty("java.io.tmpdir"));

        boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        Path testDir = baseDir.resolve("crm-key-test-" + UUID.randomUUID());

        if (isPosix) {
            Files.createDirectory(testDir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectory(testDir);
            File f = testDir.toFile();
            f.setReadable(true, true);
            f.setWritable(true, true);
            f.setExecutable(true, true);
        }

        Path privPath = testDir.resolve("crm-test-private.pem");
        Path pubPath = testDir.resolve("crm-test-public.pem");

        try {
            if (isPosix) {
                Files.createFile(privPath, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.createFile(pubPath, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
            } else {
                Files.createFile(privPath);
                privPath.toFile().setReadable(true, true);
                privPath.toFile().setWritable(true, true);
                Files.createFile(pubPath);
            }

            Files.writeString(privPath, privPem, StandardCharsets.UTF_8);
            Files.writeString(pubPath, pubPem, StandardCharsets.UTF_8);

            JwtProperties properties = new JwtProperties();
            properties.setEnabled(true);
            properties.setPrivateKeyLocation(privPath.toAbsolutePath().toString());
            properties.setPublicKeyLocation(pubPath.toAbsolutePath().toString());

            RSAPrivateKey loadedPriv = keyLoader.loadPrivateKey(properties);
            RSAPublicKey loadedPub = keyLoader.loadPublicKey(properties);

            assertThat(loadedPriv).isNotNull();
            assertThat(loadedPriv.getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
            assertThat(loadedPub).isNotNull();
            assertThat(loadedPub.getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
        } finally {
            try {
                Files.deleteIfExists(privPath);
                Files.deleteIfExists(pubPath);
                Files.deleteIfExists(testDir);
            } catch (IOException ignored) {
            }
        }

        // Verificación de limpieza determinista
        assertThat(Files.exists(privPath)).isFalse();
        assertThat(Files.exists(pubPath)).isFalse();
        assertThat(Files.exists(testDir)).isFalse();
    }

    @Test
    @DisplayName("Error al especificar archivo inexistente")
    void loadKeys_NonExistentFile_ThrowsIllegalStateException() {
        JwtProperties properties = new JwtProperties();
        properties.setEnabled(true);
        properties.setPrivateKeyLocation("/ruta/inexistente/clave-privada.pem");
        properties.setPublicKeyLocation("/ruta/inexistente/clave-publica.pem");

        assertThatThrownBy(() -> keyLoader.loadPrivateKey(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no existe");

        assertThatThrownBy(() -> keyLoader.loadPublicKey(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("Error si las propiedades de clave están ausentes")
    void loadKeys_MissingProperties_ThrowsIllegalStateException() {
        JwtProperties properties = new JwtProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> keyLoader.loadPrivateKey(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falta la clave privada");

        assertThatThrownBy(() -> keyLoader.loadPublicKey(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falta la clave pública");
    }
}
