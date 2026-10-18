package com.urleads.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class EurekaConfigurationTest {

    @Test
    @DisplayName("Perfil por defecto: spring.application.name es crm-service y Eureka client esta desactivado")
    void defaultProfile_eurekaDisabled() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder()
                .sources(TestConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("default")
                .run()) {

            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.application.name")).isEqualTo("crm-service");
            assertThat(env.getProperty("eureka.client.enabled", Boolean.class)).isFalse();

            EurekaClientConfigBean config = context.getBean(EurekaClientConfigBean.class);
            assertThat(config.isEnabled()).isFalse();
        }
    }

    @Test
    @DisplayName("Perfil local: Eureka client esta desactivado")
    void localProfile_eurekaDisabled() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder()
                .sources(TestConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("local")
                .run()) {

            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.application.name")).isEqualTo("crm-service");
            assertThat(env.getProperty("eureka.client.enabled", Boolean.class)).isFalse();

            EurekaClientConfigBean config = context.getBean(EurekaClientConfigBean.class);
            assertThat(config.isEnabled()).isFalse();
        }
    }

    @Test
    @DisplayName("Perfil integrado: Eureka client activo con URL default http://discovery:8761/eureka/ y binding verificado")
    void integratedProfile_eurekaEnabled_defaultUrl() {
        String prev = System.getProperty("eureka.client.enabled");
        try {
            System.clearProperty("eureka.client.enabled");
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder()
                    .sources(TestConfig.class)
                    .web(WebApplicationType.NONE)
                    .profiles("integrated")
                    .run()) {

                Environment env = context.getEnvironment();
                assertThat(env.getProperty("spring.application.name")).isEqualTo("crm-service");
                assertThat(env.getProperty("eureka.client.enabled", Boolean.class)).isTrue();
                assertThat(env.getProperty("eureka.client.register-with-eureka", Boolean.class)).isTrue();
                assertThat(env.getProperty("eureka.client.fetch-registry", Boolean.class)).isTrue();
                assertThat(env.getProperty("eureka.client.service-url.defaultZone"))
                        .isEqualTo("http://discovery:8761/eureka/");

                EurekaClientConfigBean config = context.getBean(EurekaClientConfigBean.class);
                assertThat(config.isEnabled()).isTrue();
                assertThat(config.isRegisterWithEureka()).isTrue();
                assertThat(config.isFetchRegistry()).isTrue();
                assertThat(config.getEurekaServerServiceUrls("defaultZone"))
                        .contains("http://discovery:8761/eureka/");
            }
        } finally {
            if (prev != null) {
                System.setProperty("eureka.client.enabled", prev);
            }
        }
    }

    @Test
    @DisplayName("Perfil integrado: sobreescritura de URL para localhost:8761 y binding verificado")
    void integratedProfile_eurekaEnabled_localhostOverride() {
        String prev = System.getProperty("eureka.client.enabled");
        try {
            System.clearProperty("eureka.client.enabled");
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder()
                    .sources(TestConfig.class)
                    .web(WebApplicationType.NONE)
                    .profiles("integrated")
                    .properties("EUREKA_SERVER_URL=http://localhost:8761/eureka/")
                    .run()) {

                Environment env = context.getEnvironment();
                assertThat(env.getProperty("eureka.client.enabled", Boolean.class)).isTrue();
                assertThat(env.getProperty("eureka.client.service-url.defaultZone"))
                        .isEqualTo("http://localhost:8761/eureka/");

                EurekaClientConfigBean config = context.getBean(EurekaClientConfigBean.class);
                assertThat(config.getEurekaServerServiceUrls("defaultZone"))
                        .contains("http://localhost:8761/eureka/");
            }
        } finally {
            if (prev != null) {
                System.setProperty("eureka.client.enabled", prev);
            }
        }
    }

    @Configuration
    @EnableConfigurationProperties(EurekaClientConfigBean.class)
    static class TestConfig {
    }
}
