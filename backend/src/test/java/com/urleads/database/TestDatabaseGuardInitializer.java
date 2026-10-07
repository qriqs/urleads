package com.urleads.database;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inicializador de contexto que valida y aplica obligatoriamente la configuracion
 * de la base de datos de pruebas aislada (TEST_DB_*).
 *
 * Se ejecuta de forma fail-fast ANTES de que DataSource o Flyway intenten conectarse.
 * Exige:
 * 1. Existencia obligatoria de TEST_DB_URL, TEST_DB_USERNAME y TEST_DB_PASSWORD (sin fallback a DB_*).
 * 2. Que el host apunte exclusivamente a loopback local (127.0.0.1 o localhost).
 * 3. Que el nombre de la base de datos termine en '_test' para impedir la ejecucion sobre desarrollo o produccion.
 * 4. Jamas imprime secretos ni contraseñas en mensajes de error.
 */
public class TestDatabaseGuardInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("^jdbc:postgresql://(?<host>[^:/?#]+)(?::(?<port>\\d+))?/(?<dbname>[^?#]+)(?:\\?.*)?$");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();

        String url = resolveProperty(env, "TEST_DB_URL", "test.db.url");
        String username = resolveProperty(env, "TEST_DB_USERNAME", "test.db.username");
        String password = resolveProperty(env, "TEST_DB_PASSWORD", "test.db.password");

        validateTestDatabaseConfiguration(url, username, password);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.datasource.url", url.trim());
        overrides.put("spring.datasource.username", username.trim());
        overrides.put("spring.datasource.password", password);

        env.getPropertySources().addFirst(new MapPropertySource("testDatabaseGuardOverrides", overrides));
    }

    public static void validateTestDatabaseConfiguration(String url, String username, String password) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Configuracion de pruebas incompleta: TEST_DB_URL es obligatorio para DatabaseMigrationIT " +
                    "y debe apuntar a una base de datos local aislada cuyo nombre termine en '_test'."
            );
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Configuracion de pruebas incompleta: TEST_DB_USERNAME es obligatorio para DatabaseMigrationIT."
            );
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Configuracion de pruebas incompleta: TEST_DB_PASSWORD es obligatorio para DatabaseMigrationIT."
            );
        }

        Matcher matcher = JDBC_URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "Formato de URL invalido: TEST_DB_URL debe tener la forma 'jdbc:postgresql://127.0.0.1:PUERTO/NOMBRE_DB'."
            );
        }

        String host = matcher.group("host");
        if (!"127.0.0.1".equalsIgnoreCase(host) && !"localhost".equalsIgnoreCase(host)) {
            throw new IllegalStateException(
                    "Proteccion de aislamiento: TEST_DB_URL debe apuntar a una instancia local de PostgreSQL " +
                    "(127.0.0.1 o localhost) para evitar conexiones a servidores remotos o compartidos."
            );
        }

        String dbName = matcher.group("dbname");
        if (!dbName.endsWith("_test")) {
            throw new IllegalStateException(
                    "Proteccion de aislamiento: el nombre de la base de datos de pruebas debe terminar en '_test' " +
                    "(por ejemplo, 'urleads_test') para prevenir mutaciones accidentales en la base de datos de desarrollo."
            );
        }
    }

    private static String resolveProperty(ConfigurableEnvironment env, String envKey, String propKey) {
        String val = env.getProperty(envKey);
        if (val == null || val.trim().isEmpty()) {
            val = env.getProperty(propKey);
        }
        return val;
    }
}
