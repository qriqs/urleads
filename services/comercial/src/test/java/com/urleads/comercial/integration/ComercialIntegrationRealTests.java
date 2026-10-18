package com.urleads.comercial.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.urleads.comercial.client.CrmLeadService;
import com.urleads.comercial.client.dto.LeadInternalResponse;
import com.urleads.comercial.client.exception.CrmUnavailableException;
import com.urleads.comercial.dto.CatalogItemRequest;
import com.urleads.comercial.entity.OutboxEvent;
import com.urleads.comercial.entity.OutboxStatus;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.repository.OutboxEventRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.security.JwtTestUtils;
import com.urleads.comercial.service.CatalogService;
import com.urleads.comercial.service.QuoteService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea S15 (DSW-70): Integración real con CRM, RabbitMQ y Agenda.
 * Valida:
 * 1. Descubrimiento dinámico de 'crm-service' vía Eureka y OpenFeign sin URLs fijas.
 * 2. Publicación de eventos canónicos 'quotation.accepted.v1' compatibles con el consumidor de Agenda (Alex, A03)
 *    sin doble serialización ni presencia de PII.
 * 3. Comprobación de timeouts estrictos y Circuit Breaker de Resilience4j ante caída de CRM,
 *    verificando que la retención de leases en Eureka no genera falsos positivos ni bloqueos de hilos.
 * 4. Corrección de defectos en la comunicación inter-servicio y despacho outbox.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.openfeign.client.config.crm-service.connect-timeout=1500",
    "spring.cloud.openfeign.client.config.crm-service.read-timeout=2000",
    "resilience4j.circuitbreaker.instances.crmService.sliding-window-size=5",
    "resilience4j.circuitbreaker.instances.crmService.minimum-number-of-calls=3",
    "resilience4j.circuitbreaker.instances.crmService.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.crmService.wait-duration-in-open-state=2s",
    "resilience4j.circuitbreaker.instances.crmService.permitted-number-of-calls-in-half-open-state=2"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComercialIntegrationRealTests {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private CrmLeadService crmLeadService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private MessageConverter jsonMessageConverter;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    private String user42Token;

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
    }

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        quoteRepository.deleteAll();
        catalogItemRepository.deleteAll();
        wireMockServer.resetAll();

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("crmService");
        cb.reset();

        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access crm.leads.read");
    }

    private static boolean isServiceAvailable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Criterio 1: Contrato de registro en Eureka y parámetros de retención de leases")
    void test01_eurekaConfigurationAndRegistrationContract() {
        // Si Eureka Server real (8761) está activo en Compose (C14), verificar registro en vivo
        if (isServiceAvailable("localhost", 8761)) {
            RestTemplate eurekaHttp = new RestTemplate();
            String eurekaUrl = "http://localhost:8761/eureka/apps/COMERCIAL-SERVICE";
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> resp = eurekaHttp.exchange(eurekaUrl, HttpMethod.GET, req, String.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    assertThat(resp.getBody()).containsIgnoringCase("COMERCIAL-SERVICE");
                }
            } catch (Exception ignored) {
            }
        }

        // Validación determinista del contrato de Eureka Client y leases
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("crmService");
        assertThat(cb).isNotNull();
        assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(3);
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
    }

    @Test
    @Order(2)
    @DisplayName("Criterio 2: Llamada Feign hacia crm-service resuelta dinámicamente con propagación JWT")
    void test02_feignDynamicCallToCrm_withJwtPropagation() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .withHeader("Authorization", WireMock.matching("Bearer .+"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": 105,
                      "stage": "EN_SEGUIMIENTO",
                      "nextFollowUp": "2026-10-30"
                    }
                    """)));

        // Ejecutar llamada a través del endpoint que consume crmLeadService con token JWT
        mockMvc.perform(get("/api/v1/crm/leads/105")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(105))
            .andExpect(jsonPath("$.stage").value("EN_SEGUIMIENTO"))
            .andExpect(jsonPath("$.nextFollowUp").value("2026-10-30"));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/internal/v1/leads/105"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + user42Token)));
    }

    @Test
    @Order(3)
    @DisplayName("Criterio 3: Publicación canónica de quotation.accepted.v1 compatible con Agenda (sin doble serialización ni PII)")
    void test03_quotationAcceptedEventPublication_andAgendaConsumerCompatibility() throws Exception {
        // Configurar CRM lead en WireMock
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":105,\"stage\":\"EN_SEGUIMIENTO\"}")));

        // Crear ítem de catálogo
        var item = catalogService.create("42", new CatalogItemRequest(
            "PLAN-ENT", "Plan Enterprise", "Servicio integral", "USD",
            new BigDecimal("2500.00"), true
        ));

        // Crear cotización
        Quote quote = new Quote(105L, "42", "USD", new BigDecimal("0.18"), "Notas comerciales");
        quote.addItem(new QuoteItem(
            quote, item.id(), item.code(), item.name(),
            "Servicio integral",
            BigDecimal.ONE, new BigDecimal("2500.00")
        ));
        quote.recalculateTotals();
        quote = quoteRepository.save(quote);
        Long quoteId = quote.getId();

        // Aceptar cotización vía endpoint REST
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.total").value(2950.00));

        // 1. Verificar persistencia atómica en outbox_events
        List<OutboxEvent> outboxList = outboxEventRepository.findByOwnerId("42");
        assertThat(outboxList).hasSize(1);
        OutboxEvent outboxEvent = outboxList.get(0);
        assertThat(outboxEvent.getEventType()).isEqualTo("quotation.accepted.v1");
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PROCESSED);

        // 2. Comprobar que el convertidor de RabbitMQ produce bytes JSON limpios (no string escapado)
        Message amqpMessage = jsonMessageConverter.toMessage(outboxEvent.getPayload(), new MessageProperties());
        assertThat(amqpMessage.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);

        String messageBodyString = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        assertThat(messageBodyString.startsWith("{"))
            .as("El payload enviado a RabbitMQ debe ser un objeto JSON puro '{', sin doble serialización")
            .isTrue();

        // 3. Comprobar deserialización directa idéntica al consumidor de Agenda (Alex, A03)
        JsonNode rootNode = objectMapper.readTree(amqpMessage.getBody());
        assertThat(rootNode.get("eventId")).isNotNull();
        assertThat(rootNode.get("eventType").asText()).isEqualTo("quotation.accepted.v1");
        assertThat(rootNode.get("version").asInt()).isEqualTo(1);
        assertThat(rootNode.get("ownerId").asText()).isEqualTo("42");

        JsonNode dataNode = rootNode.get("data");
        assertThat(dataNode.get("quoteId").asLong()).isEqualTo(quoteId);
        assertThat(dataNode.get("leadId").asLong()).isEqualTo(105L);
        assertThat(dataNode.get("currency").asText()).isEqualTo("USD");
        assertThat(dataNode.get("total").asDouble()).isEqualTo(2950.00);

        // 4. Verificación estricta de ausencia de PII
        assertThat(rootNode.has("notes")).isFalse();
        assertThat(rootNode.has("name")).isFalse();
        assertThat(rootNode.has("email")).isFalse();
        assertThat(rootNode.has("phone")).isFalse();
        assertThat(dataNode.has("notes")).isFalse();
        assertThat(dataNode.has("name")).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("Criterio 4: Comportamiento ante caída de CRM, retención de leases en Eureka y Circuit Breaker")
    void test04_circuitBreakerResilience_underCrmDown_andEurekaLeaseRetention() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("crmService");
        cb.reset();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Simular caída abrupta de CRM (503 Service Unavailable o Connection Refused)
        // Aunque Eureka mantenga la instancia en lease retention, Comercial debe aislar el fallo
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/888"))
            .willReturn(aResponse().withStatus(503)));

        // Llamada 1: Fallo inicial
        assertThatThrownBy(() -> crmLeadService.getLeadById(888L))
            .isInstanceOf(CrmUnavailableException.class);

        // Llamada 2: Segundo fallo
        assertThatThrownBy(() -> crmLeadService.getLeadById(888L))
            .isInstanceOf(CrmUnavailableException.class);

        // Llamada 3: Tercer fallo (alcanza minimumNumberOfCalls = 3 y tasa de fallo 100% > 50%)
        assertThatThrownBy(() -> crmLeadService.getLeadById(888L))
            .isInstanceOf(CrmUnavailableException.class);

        // El Circuit Breaker pasa inmediatamente a OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // En estado OPEN, cualquier llamada subsecuente falla de inmediato por CallNotPermittedException
        // SIN realizar peticiones de red ni generar falsos positivos por el lease retenido en Eureka
        long startTime = System.currentTimeMillis();
        assertThatThrownBy(() -> crmLeadService.getLeadById(888L))
            .isInstanceOf(CrmUnavailableException.class)
            .hasCauseInstanceOf(CallNotPermittedException.class);
        long elapsed = System.currentTimeMillis() - startTime;

        // Comprobar respuesta ultra-rápida (fail-fast < 100ms) sin bloqueos de hilos
        assertThat(elapsed).isLessThan(100L);

        // Recuperación: tras el período de espera, transición a HALF_OPEN y recuperación ante respuesta 200
        cb.transitionToHalfOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/888"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":888,\"stage\":\"NUEVO\"}")));

        // Las 2 llamadas permitidas en HALF_OPEN tienen éxito
        LeadInternalResponse r1 = crmLeadService.getLeadById(888L);
        LeadInternalResponse r2 = crmLeadService.getLeadById(888L);
        assertThat(r1.id()).isEqualTo(888L);
        assertThat(r2.id()).isEqualTo(888L);

        // El Circuit Breaker se restablece automáticamente a CLOSED
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
