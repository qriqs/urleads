package com.urleads.comercial.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.urleads.comercial.client.CrmLeadService;
import com.urleads.comercial.client.exception.CrmUnavailableException;
import com.urleads.comercial.config.RabbitMqConfig;
import com.urleads.comercial.dto.QuoteCreateRequest;
import com.urleads.comercial.dto.QuoteItemRequest;
import com.urleads.comercial.entity.OutboxEvent;
import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.entity.QuoteStatus;
import com.urleads.comercial.repository.CommercialOrderRepository;
import com.urleads.comercial.repository.OutboxEventRepository;
import com.urleads.comercial.repository.QuoteRepository;
import com.urleads.comercial.security.JwtTestUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommercialResilienceAndFaultToleranceTests {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private CommercialOrderRepository commercialOrderRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

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
        registry.add("spring.cloud.openfeign.client.config.crm-service.read-timeout", () -> "1000");
        registry.add("spring.cloud.openfeign.client.config.crm-service.connect-timeout", () -> "1000");
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        commercialOrderRepository.deleteAll();
        outboxEventRepository.deleteAll();
        quoteRepository.deleteAll();

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("crmService");
        cb.reset();

        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access crm.leads.read");
    }

    @Test
    @DisplayName("RabbitMQ outage resilience: quote acceptance and outbox persist in DB even if broker publish fails")
    void rabbitMqOutage_persistsQuoteAndOutboxAtomically() throws Exception {
        // 1. Create a draft quote
        Quote quote = new Quote(105L, "42", "USD", new BigDecimal("0.1800"), "Cotización para prueba de outbox");
        quote.setStatus(QuoteStatus.DRAFT);
        QuoteItem item = new QuoteItem(quote, null, "ITM-01", "Item", "Desc", BigDecimal.ONE, new BigDecimal("200.00"));
        quote.addItem(item);
        quote = quoteRepository.save(quote);
        Long quoteId = quote.getId();

        // 2. Mock WireMock CRM response for lead validation
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/105"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "id": 105, "stage": "EN_SEGUIMIENTO" }
                    """)));

        // 3. Simulate RabbitMQ broker down: convertAndSend throws AmqpException
        Mockito.doThrow(new AmqpException("Broker connection refused"))
            .when(rabbitTemplate)
            .convertAndSend(eq(RabbitMqConfig.EVENTS_EXCHANGE), eq(RabbitMqConfig.QUOTATION_ACCEPTED_ROUTING_KEY), any(Object.class));

        // 4. Accept quote via endpoint: should succeed with HTTP 200 because outbox pattern decouples broker delivery
        mockMvc.perform(post("/api/v1/quotes/" + quoteId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", equalTo(quoteId.intValue())))
            .andExpect(jsonPath("$.status", equalTo("ACCEPTED")));

        // 5. Verify SQL database state: quote is ACCEPTED and outbox event exists in DB
        Quote updatedQuote = quoteRepository.findById(quoteId).orElseThrow();
        assertThat(updatedQuote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);

        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertThat(outboxList).hasSize(1);
        OutboxEvent event = outboxList.get(0);
        assertThat(event.getEventType()).isEqualTo("quotation.accepted.v1");
        assertThat(event.getPayload()).contains("\"quoteId\":" + quoteId);
    }

    @Test
    @DisplayName("CRM service outage resilience: 500/503 from CRM returns clean 503 SERVICE_UNAVAILABLE")
    void crmOutage_returnsClean503ServiceUnavailable() throws Exception {
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/internal/v1/leads/999"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));

        mockMvc.perform(get("/api/v1/crm/leads/999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code", equalTo("CRM_UNAVAILABLE")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("El servicio CRM no se encuentra disponible temporalmente.")));
    }

    @Test
    @DisplayName("Monetary exactness resilience: multi-item rounding across states adheres strictly to HALF_UP")
    void monetaryCalculationResilience_acrossStates() {
        Quote quote = new Quote(100L, "42", "USD", new BigDecimal("0.1800"), "Prueba de exactitud");
        quote.setStatus(QuoteStatus.DRAFT);

        // Add items with fractional cents product
        // 3 items of 33.333 -> quantity 3, unitPrice 11.11 -> 33.33
        QuoteItem item1 = new QuoteItem(quote, null, "A", "Item A", "", new BigDecimal("3.00"), new BigDecimal("11.11"));
        // 7 items of 14.285 -> quantity 7, unitPrice 2.04 -> 14.28
        QuoteItem item2 = new QuoteItem(quote, null, "B", "Item B", "", new BigDecimal("7.00"), new BigDecimal("2.04"));

        quote.addItem(item1);
        quote.addItem(item2);

        // Subtotal: 33.33 + 14.28 = 47.61
        assertThat(quote.getSubtotal()).isEqualByComparingTo(new BigDecimal("47.61"));
        // Tax: 47.61 * 0.18 = 8.5698 -> 8.57
        assertThat(quote.getTax()).isEqualByComparingTo(new BigDecimal("8.57"));
        // Total: 47.61 + 8.57 = 56.18
        assertThat(quote.getTotal()).isEqualByComparingTo(new BigDecimal("56.18"));

        // Transition states without altering totals
        quote.transitionTo(QuoteStatus.SENT);
        assertThat(quote.getTotal()).isEqualByComparingTo(new BigDecimal("56.18"));

        quote.transitionTo(QuoteStatus.ACCEPTED);
        assertThat(quote.getTotal()).isEqualByComparingTo(new BigDecimal("56.18"));
    }
}
