package com.urleads.comercial.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.urleads.comercial.client.dto.LeadInternalResponse;
import com.urleads.comercial.client.exception.CrmUnavailableException;
import com.urleads.comercial.exception.ResourceNotFoundException;
import com.urleads.comercial.security.JwtTestUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CrmClientResilienceTests {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CrmLeadService crmLeadService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private String validToken;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("crm.service.url", () -> "http://localhost:" + wireMockServer.port());
        // Configure short timeouts for fast test runs
        registry.add("spring.cloud.openfeign.client.config.crm-service.read-timeout", () -> "1000");
        registry.add("spring.cloud.openfeign.client.config.crm-service.connect-timeout", () -> "1000");
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("crmService");
        cb.reset();
        validToken = JwtTestUtils.generateValidToken("42", "comercial.access crm.leads.read");
    }

    @Test
    @DisplayName("GET /api/v1/crm/leads/{id} consumes CRM lead successfully with JWT propagation")
    void getLead_success_withJwtPropagation() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": 105,
                      "stage": "EN_SEGUIMIENTO",
                      "nextFollowUp": "2026-10-25"
                    }
                    """)));

        mockMvc.perform(get("/api/v1/crm/leads/105")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(105))
            .andExpect(jsonPath("$.stage").value("EN_SEGUIMIENTO"))
            .andExpect(jsonPath("$.nextFollowUp").value("2026-10-25"));

        // Verify Feign forwarded the Bearer token in the Authorization header
        wireMockServer.verify(getRequestedFor(urlEqualTo("/internal/v1/leads/105"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + validToken)));
    }

    @Test
    @DisplayName("GET /api/v1/crm/leads/{id} returns 404 when lead is not found in CRM")
    void getLead_notFound_returns404() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/999"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "status": 404,
                      "code": "NOT_FOUND",
                      "message": "Lead no encontrado"
                    }
                    """)));

        mockMvc.perform(get("/api/v1/crm/leads/999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/crm/leads/{id} returns 403 when CRM rejects missing scope")
    void getLead_forbidden_returns403() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "status": 403,
                      "code": "FORBIDDEN",
                      "message": "Token sin scope crm.leads.read"
                    }
                    """)));

        mockMvc.perform(get("/api/v1/crm/leads/105")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/crm/leads/{id} returns 503 clean when CRM throws 500 error")
    void getLead_crmError500_triggersFallback503() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        mockMvc.perform(get("/api/v1/crm/leads/105")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.code").value("CRM_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("El servicio CRM no se encuentra disponible temporalmente."));
    }

    @Test
    @DisplayName("Timeout on CRM call triggers CircuitBreaker fallback to 503")
    void getLead_timeout_triggersFallback503() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(2000) // Exceeds 1000ms read-timeout
                .withBody("{\"id\": 105}")));

        mockMvc.perform(get("/api/v1/crm/leads/105")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.code").value("CRM_UNAVAILABLE"));
    }

    @Test
    @DisplayName("CircuitBreaker transitions to OPEN after consecutive failures and fast-fails calls")
    void circuitBreaker_opens_andFastFails() {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse().withStatus(503)));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("crmService");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Make calls that fail to trigger state transition
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> crmLeadService.getLeadById(105L))
                .isInstanceOf(CrmUnavailableException.class);
        }

        // Verify CircuitBreaker is now OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call fails immediately with fast failure via CircuitBreaker fallback
        assertThatThrownBy(() -> crmLeadService.getLeadById(105L))
            .isInstanceOf(CrmUnavailableException.class);
    }
}
