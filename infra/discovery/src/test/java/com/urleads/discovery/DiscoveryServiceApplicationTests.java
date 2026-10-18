package com.urleads.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DiscoveryServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Context loads and application name is discovery-service")
    void contextLoadsAndApplicationNameMatches() {
        assertThat(applicationContext).isNotNull();
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("discovery-service");
    }

    @Test
    @DisplayName("Server port is configured to 8761")
    void serverPortIs8761() {
        assertThat(environment.getProperty("server.port"))
                .isEqualTo("8761");
    }

    @Test
    @DisplayName("Eureka server disables self-registration and fetch-registry")
    void eurekaSelfRegistrationDisabled() {
        assertThat(environment.getProperty("eureka.client.register-with-eureka", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("eureka.client.fetch-registry", Boolean.class))
                .isFalse();
    }

    @Test
    @DisplayName("Eureka server mode is enabled via marker bean and annotation")
    void eurekaServerMarkerBeanPresent() {
        assertThat(DiscoveryServiceApplication.class.isAnnotationPresent(EnableEurekaServer.class))
                .isTrue();
        assertThat(applicationContext.containsBean("eurekaServerMarkerBean"))
                .isTrue();
    }

    @Test
    @DisplayName("Negative invariant: No relational database, JPA, or RabbitMQ beans exist")
    void noDatabaseOrMessageBrokerBeans() {
        assertThat(applicationContext.containsBean("dataSource")).isFalse();
        assertThat(applicationContext.containsBean("entityManagerFactory")).isFalse();
        assertThat(applicationContext.containsBean("rabbitTemplate")).isFalse();
    }
}
