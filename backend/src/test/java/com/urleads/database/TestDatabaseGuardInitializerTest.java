package com.urleads.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseGuardInitializerTest {

    @Test
    void preservesPasswordWhitespaceWhenApplyingTestConfiguration() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            String testPassword = " synthetic-test-value ";
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("testInputs", Map.of(
                            "TEST_DB_URL", "jdbc:postgresql://127.0.0.1:54333/urleads_test",
                            "TEST_DB_USERNAME", "test_user",
                            "TEST_DB_PASSWORD", testPassword
                    ))
            );

            new TestDatabaseGuardInitializer().initialize(context);

            assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                    .isEqualTo(testPassword);
        }
    }

    @Test
    @DisplayName("Falla si TEST_DB_URL es nulo o vacio")
    void failsWhenUrlMissing() {
        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(null, "user", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_URL es obligatorio");

        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration("   ", "user", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_URL es obligatorio");
    }

    @Test
    @DisplayName("Falla si TEST_DB_USERNAME es nulo o vacio")
    void failsWhenUsernameMissing() {
        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://127.0.0.1:54332/urleads_test", null, "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_USERNAME es obligatorio");

        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://127.0.0.1:54332/urleads_test", "   ", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_USERNAME es obligatorio");
    }

    @Test
    @DisplayName("Falla si TEST_DB_PASSWORD es nulo o vacio")
    void failsWhenPasswordMissing() {
        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://127.0.0.1:54332/urleads_test", "user", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_PASSWORD es obligatorio");

        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://127.0.0.1:54332/urleads_test", "user", "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_PASSWORD es obligatorio");
    }

    @Test
    @DisplayName("Falla si el host no es local (rechaza servidores remotos)")
    void failsWhenHostNotLocal() {
        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://db.remote-server.test:5432/urleads_test", "user", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("127.0.0.1 o localhost");
    }

    @Test
    @DisplayName("Falla si el nombre de la base de datos no termina en _test (rechaza base de desarrollo)")
    void failsWhenDatabaseNameDoesNotEndWithTestSuffix() {
        assertThatThrownBy(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/urleads", "user", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debe terminar en '_test'");
    }

    @Test
    @DisplayName("Acepta configuraciones locales validas con sufijo _test")
    void acceptsValidLocalTestConfigurations() {
        assertThatCode(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://127.0.0.1:54332/urleads_test", "user", "pass"))
                .doesNotThrowAnyException();

        assertThatCode(() -> TestDatabaseGuardInitializer.validateTestDatabaseConfiguration(
                "jdbc:postgresql://localhost:54332/urleads_test?sslmode=disable", "user", "pass"))
                .doesNotThrowAnyException();
    }
}
